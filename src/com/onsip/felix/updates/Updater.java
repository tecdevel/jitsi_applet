package com.onsip.felix.updates;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.zip.ZipEntry;

import org.apache.felix.framework.util.ZipFileX;
import org.osgi.framework.Version;

/**
 * This Updater class provides the functionality for updating the Felix
 * bundles that support the Jitsi applet.  There are some subtleties in the
 * implementation of this class that need to be addressed due to the way
 * java caching and the felix cache play off each other.
 *
 * It's worth pointing out that we're circumventing Felix's internal
 * support for bundle updates. As a first go, the functionality was a bit
 * flaky. That is to say calling the update function on any given set
 * of bundles would effectively crash
 * the JVM after throwing several exceptions. There are also lots
 * of complexities around dependencies, threading, class loaders, and
 * the life cycle of the bundle.  Generally, Felix did a whole lot better
 * recognizing when a bundle was missing and filling in those missing gaps
 * when it loaded.  So to summarize, updating the bundles was a buggy nightmare,
 * while removing individual bundle sets proved to be much more resilient.
 * Further, because it's an applet, we really don't need the update to
 * accommodate a long lived application that can refresh itself at run time.
 *
 * We need to consider the way in which the Jitsi cache works alongside Java cache, but
 * for now let's just focus on the way this updater works when the Java cache
 * store is inactive.
 *
 * There are 3 additions to the Felix properties file that are used. By
 * default, the updater will do nothing.
 *
 * The <i>onsip.options.update</i> property which accepts either a value
 * of yes or anything else.  Anything except for <i>yes</i> designates that
 * the update routine will do nothing. A value of <i>yes</i> initiates the updater.
 *
 * With onsip.options.update=yes, the first thing that happens is that Updater
 * will read the <i>onsip.cache.version</i>.  A valid version number is kept stored
 * in the felix cache directory which reflects the current version of the cache
 * store.  This cache store version is unique to the Jitsi applet and not
 * used within Jitsi.  When the local cache version and the cache version specified
 * on the host server properties file are out of synch, then the felix cache
 * are eliminated as soon as the application loads.  This
 * forces felix to grab all the bundles off the server (well, kind of.
 * At least with Java cache turned off it does).
 *
 * If the hosted cache version and the local cache version are completely
 * valid and are completely in sync, or if any of the version files (on the server or locally)
 * don't exist or are invalid in some way then we move to an alternate
 * method for updating the bundles.  Effectively, the Update will loop
 * over all the referenced jar files and identify those that keep a version number
 * in the form http://<host>/sc-bundles/somejar.jar?version=X.X.X.
 * When a valid version parameter is provided, that version is compared to the
 * locally cached bundle's MANIFEST file to verify that the two
 * version are identical. If version X.X.X parameter is not the same as the version
 * found in the cached jar's MANIFEST (Bundle-Version property) file then
 * the bundle is removed and that forces Felix to replace it off the server.
 *
 * IMPORTANT: the most important point to keep in mind is that the version
 * specified in the <i>version</i> parameter MUST BE the same value as the
 * Bundle-Version property in the MANIFEST file of the NEW bundle on the host server
 * that is going to be deployed in the update. This process is a bit manual.
 * The effect of having a version parameter in the jar url in the
 * properties file that is different from the Bundle-Version in the
 * actual MANIFEST of the Jar Bundle that's deploying will cause the Bundle
 * on the end user's machine to be deleted and retrieved from the server
 * everytime it loads.
 *
 * Here's the role that the Java Cache Storage seems to play in the update process
 * if it's enabled.  When Java intercepts the download it verifies that the cached jar in Java's own
 * repository is in some way different than the version on the server. So
 * in reality, if the locally cached version is the same as the server version
 * in name, size, modified timestamp, then
 * the version from cache is retrieved.
 *
 *
 */
public class Updater
{
    private final static java.util.logging.Logger m_logger =
        java.util.logging.Logger.getLogger(Updater.class.getName());

    private final static String OPTION_UPDATE_YES = "yes";
    private final static String OPTION_UPDATE_NO = "no";
    private final static String CACHE_VERSION_FILE = "cache.version";
    private final static String CACHE_LOCK_FILE = "cache.lock";
    private final static String FELIX_CACHE_STORE = "felix-cache";
    private final static String JITSI_CACHE_STORE = ".sip-communicator";
    private final static String SEPARATOR =
        System.getProperty("file.separator");

    private static File getFelixDir()
    {
        String dir = System.getProperty("deployment.user.cachedir");
        if (dir == null) {
            dir = System.getProperty("user.home");
        }
        m_logger.log(Level.FINE, "Using felix cache dir - " + dir);
        File fFelixCache = new File(dir + SEPARATOR + FELIX_CACHE_STORE);
        return fFelixCache;
    }

    public static boolean isCacheLocked()
    {
        FileOutputStream fos = null;
        FileChannel fc = null;
        boolean isLocked = false;
        FileLock lock = null;
        try
        {
            File felixCache = getFelixDir();
            if (felixCache.exists())
            {
                File fCacheLock = new File(felixCache.getAbsolutePath() +
                    SEPARATOR + CACHE_LOCK_FILE);

                fos = new FileOutputStream(fCacheLock);
                fc = fos.getChannel();
                try
                {
                    lock = fc.tryLock();
                }
                catch (Exception ex)
                {
                    isLocked = true;
                    throw new Exception("Unable to lock bundle cache: " + ex);
                }
                try
                {
                    if (lock != null)
                    {
                        lock.release();
                    }
                }
                catch(Exception ex)
                {
                    // failed on releasing the lock
                    // no worries
                }
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: readCacheLock : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        finally
        {
            try
            {
                if (fos != null)
                {
                    fos.close();
                }
                if (fc != null)
                {
                    fc.close();
                }
            }
            catch (Exception ex2)
            {
                // Ignore.
            }
        }

        return isLocked;
    }

    private static String readCacheVersion()
        throws Exception
    {
        InputStream is = null;
        BufferedReader br = null;
        String v = "";
        try
        {
            File felixCache = getFelixDir();
            if (felixCache.exists())
            {
                File fCache = new File(felixCache.getAbsolutePath() +
                    SEPARATOR + CACHE_VERSION_FILE);
                if (fCache.exists())
                {
                    is = new FileInputStream(fCache);
                    br = new BufferedReader(new InputStreamReader(is));
                    v = br.readLine();
                }
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: readCacheVersion : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        finally
        {
            if (br != null) br.close();
            if (is != null) is.close();
        }
        if (v != null)
        {
            m_logger.log(Level.INFO, "Local Cache.Version " + v);
            return v.trim();
        }
        return "";
    }

    /*
     *  Save current revision location.
     */
    private static synchronized void setCacheVersion(String cacheVersion)
        throws Exception
    {
        OutputStream os = null;
        BufferedWriter bw = null;
        try
        {
            File felixCache = getFelixDir();
            if (felixCache.exists())
            {
                File fCache = new File(felixCache.getAbsolutePath() +
                    SEPARATOR + CACHE_VERSION_FILE);
                os = new FileOutputStream(fCache);

                bw = new BufferedWriter(new OutputStreamWriter(os));
                bw.write(cacheVersion, 0, cacheVersion.length());
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: setCacheVersion(String) : ");
            m_logger.log(Level.SEVERE,
                "Could not create cache.version file, details " +
                    e.getMessage(), e);
        }
        finally
        {
            if (bw != null) bw.close();
            if (os != null) os.close();
        }
    }

    public static synchronized void setCacheVersion(Properties config)
    {
        try
        {
            File felixCache = getFelixDir();
            if (felixCache.exists())
            {
                File fCache = new File(felixCache.getAbsolutePath() +
                    SEPARATOR + CACHE_VERSION_FILE);
                if (!fCache.exists())
                {
                    String configVersion = config.getProperty("onsip.cache.version");
                    if (configVersion != null && configVersion.length() > 0)
                    {
                        Version v = Version.parseVersion(configVersion);
                        setCacheVersion(v.toString());
                    }
                }
            }
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE,
                "Exception :: setCacheVersion(Properties) : ");
            m_logger.log(Level.SEVERE,
                "Could not create cache.version file, details " +
                    e.getMessage(), e);
        }
    }
    private static boolean delete(File directory)
    {
        if(directory.exists())
        {
            File[] files = directory.listFiles();
            for(int i=0; i<files.length; i++)
            {
                if(files[i].isDirectory())
                {
                    delete(files[i]);
                }
                else
                {
                    files[i].delete();
                }
            }
        }
        return (directory.delete());
    }

    public static void trash()
    {
        try
        {
            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return (name.startsWith("$") &&
                        name.endsWith("$"));
                }
            };

            File felixDir = getFelixDir();
            if (felixDir.exists())
            {
                /* delete the unused bundles e.g $<bundle>$ */
                File[] files = felixDir.listFiles(filter);
                for (int i=0; i < files.length; i++)
                {
                    m_logger.log(Level.FINE, "REMOVING " +
                        files[i].getAbsolutePath());
                    delete(files[i]);
                }

                File felixCacheBak =
                    new File(felixDir.getParent() +
                        SEPARATOR + "$" + FELIX_CACHE_STORE + "$");

                /* delete unused felix cache store */
                if (felixCacheBak.exists())
                {
                    m_logger.log(Level.FINE, "REMOVING - " +
                        felixCacheBak.getAbsolutePath());
                    delete(felixCacheBak);
                }

                /* delete unused jitsi cache store */
                File jitsiCacheBak =
                    new File(felixDir.getParent() +
                        SEPARATOR + "$" + JITSI_CACHE_STORE + "$");

                if (jitsiCacheBak.exists())
                {
                    m_logger.log(Level.FINE, "REMOVING - " +
                        jitsiCacheBak.getAbsolutePath());
                    delete(jitsiCacheBak);
                }
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: trash : ");
            m_logger.log(Level.SEVERE,
                "Error while trashing felix bundle, details " +
                    e.getMessage(), e);
        }
    }

    public static void cleanFelixCacheDir()
    {
        try
        {
            m_logger.log(Level.FINE, "Clean up felix / jitsi storage");
            String dir = System.getProperty("deployment.user.cachedir");
            if (dir == null) {
                dir = System.getProperty("user.home");
            }
            m_logger.log(Level.FINE, "Using felix cache dir - " + dir);
            File fFelixCache = new File(dir + SEPARATOR + FELIX_CACHE_STORE);

            /* first, we try to delete the felix cache */
            try
            {
                if (fFelixCache.exists() && fFelixCache.isDirectory())
                {
                    String fileName = fFelixCache.getName();
                    File rename =
                        new File(fFelixCache.getParent() +
                            SEPARATOR + "$" + fileName + "$");
                    fFelixCache.renameTo(rename);
                }
            }
            catch(Exception e)
            {
                m_logger.log(Level.SEVERE, "Exception :: cleanFelixCacheDir : ");
                m_logger.log(Level.SEVERE,
                    "Error while deleting felix cache directory, " +
                        "try deleting on exit, details " + e.getMessage(), e);
            }

            /* second, we try to rename the sip communicator cache */
            File fSipCommunicator = new File(dir + SEPARATOR + JITSI_CACHE_STORE);
            try
            {
                if (fSipCommunicator.exists() && fSipCommunicator.isDirectory())
                {
                    String fileName = fSipCommunicator.getName();
                    File rename =
                        new File(fFelixCache.getParent() +
                            SEPARATOR + "$" + fileName + "$");
                    fSipCommunicator.renameTo(rename);
                }
            }
            catch(Exception e)
            {
                m_logger.log(Level.SEVERE, "Exception :: cleanFelixCacheDir : ");
                m_logger.log(Level.SEVERE,
                    "Error while deleting jitsi cache directory, " +
                        "try deleting on exit, details " + e.getMessage(), e);
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: cleanFelixCacheDir : ");
            m_logger.log(Level.SEVERE,
                "Error while deleting cache directories, " +
                    "details " + e.getMessage(), e);
        }
    }

    private static String[] getJarsFromConfig(Properties config)
    {
        try
        {
            int MAX_JARS = 100;
            String [] jarList = new String[MAX_JARS];
            int mark = 0;
            for (int i=0; i < MAX_JARS; i++)
            {
                String list = config.getProperty("felix.auto.start." + i);
                if (list != null && list.length() > 0)
                {
                    String [] jars = list.split("\\s+");
                    for (int j=0; j < jars.length; j++)
                    {
                        int idxSlash = jars[j].lastIndexOf("/");
                        if (idxSlash != -1)
                        {
                            String jar = jars[j].substring(idxSlash + 1);
                            if (jar.indexOf(".jar") != -1 &&
                                jar.indexOf("version=") != -1)
                            {
                                jarList[mark++] = jar;
                            }
                        }
                    }
                }
            }
            String[] trimJarList = new String[mark];
            System.arraycopy(jarList, 0, trimJarList, 0, mark);
            return trimJarList;
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: getJarsFromConfig : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return new String[0];
    }

    private synchronized static String getOptionUpdateProperty
        (Properties config)
    {
        String action = OPTION_UPDATE_NO;
        try
        {
            if (config != null && !config.isEmpty())
            {
                String optionUpdate = config.getProperty("onsip.options.update");
                if (optionUpdate != null && optionUpdate.length() > 0)
                {
                    optionUpdate = optionUpdate.toLowerCase();
                    if (optionUpdate.equals(OPTION_UPDATE_YES))
                    {
                        action = OPTION_UPDATE_YES;
                    }
                }
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: getOptionUpdateProperty : ");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return action;
    }

    /*
     * Here is the logic of the update:
     * none: do nothing with updates
     * otherwise:
     * if the cache.version between host and local are different,
     * then completely wipe felix cache.
     * otherwise, just check individual bundles for updates
     * rename old bundles to $<bundle>$
     *
     */
    public synchronized static void checkUpdates(Properties config)
    {
        try
        {
            File felixDir = getFelixDir();
            if (!felixDir.exists())
            {
                m_logger.log(Level.INFO,
                    "This is a new install, nothing to update");
                return;
            }

            /*
             * Set update details so we know how this framework will update itself
             */
            String update = getOptionUpdateProperty(config);
            if (update.equals(OPTION_UPDATE_NO))
            {
                m_logger.log(Level.INFO,
                    "There are no updates, we're done here");
                return;
            }
            else
            {
                /*
                 * Setting update=yes means that the updater
                 * will start writing the cache version to file
                 * if the cache versions are different between
                 * client and server config, then update the entire
                 * cache
                 *
                 * Otherwise, just search for updates at the individual
                 * bundle level
                 */
                boolean cleanGlobalCache = false;
                String configVersion = config.getProperty("onsip.cache.version");
                if (configVersion != null && configVersion.length() > 0)
                {
                    Version vConfig = null;
                    try
                    {
                        vConfig = Version.parseVersion(configVersion);

                        m_logger.log(Level.INFO, "Required Cache.Version " + vConfig);
                        if (vConfig != null)
                        {
                            String localCacheV = readCacheVersion();
                            Version vLocalCacheV = null;
                            try
                            {
                                if (localCacheV.length() == 0)
                                {
                                    localCacheV = null;
                                }
                                vLocalCacheV = Version.parseVersion(localCacheV);
                            }
                            catch(Exception e)
                            {
                                m_logger.log(Level.WARNING, "The locally held version of " +
                                    "the cache store could not be determined so " +
                                        "scrap the felix cache and download everything");
                                vLocalCacheV = null;
                            }
                            if (vLocalCacheV == null || vLocalCacheV.compareTo(vConfig) != 0)
                            {
                                m_logger.log(Level.INFO,
                                    "Attempt to delete cache store " +
                                        localCacheV);
                                cleanFelixCacheDir();
                                cleanGlobalCache = true;
                                setCacheVersion(vConfig.toString());
                            }
                            /*
                             * Do not overwrite cache.version if it's the
                             * same as the server
                             */
                        }
                    }
                    catch (Exception e)
                    {
                        m_logger.log(Level.WARNING,
                            "Exception :: checkUpdates :");
                        /*
                         * If an error is thrown, we'll just ignore and try
                         * to move forward with bundle specific updates
                         */
                        vConfig = null;
                        m_logger.log(Level.WARNING,
                            "There was an error while " +
                                "evaluating the global cache version, " +
                                    "details " + e.getMessage());
                    }
                }

                if (!cleanGlobalCache)
                {
                    String [] jarList = getJarsFromConfig(config);
                    File [] fBundles = getOustedJarList(jarList);
                    int count = 0;
                    for (int i=0; i < fBundles.length; i++)
                    {
                        if (fBundles[i] != null)
                        {
                            if (fBundles[i].exists() &&
                                fBundles[i].isDirectory())
                            {
                                count++;
                                File bundle = fBundles[i];
                                m_logger.log(Level.FINE, "Deleting folder " +
                                    bundle.getAbsolutePath());
                                String fileName = bundle.getName();
                                File anew =
                                    new File(bundle.getParent() +
                                        SEPARATOR + "$" + fileName + "$");
                                bundle.renameTo(anew);
                                anew.delete();
                            }
                        }
                    }
                    m_logger.log(Level.FINE, "Found " + count + " bundles to delete");
                }
            }
        }
        catch(Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: checkUpdates :");
            m_logger.log(Level.SEVERE, "Error while checking updates", e);
        }
    }


    @SuppressWarnings("unused")
    private static long readLastModified(File file)
        throws Exception
    {
        long last = 0;

        InputStream is = null;
        BufferedReader br = null;
        try
        {
            is = new FileInputStream(file);
            br = new BufferedReader(new InputStreamReader(is));
            last = Long.parseLong(br.readLine());
        }
        catch (Exception e)
        {
            m_logger.log(Level.WARNING, "Exception :: readLastModified :");
            m_logger.log(Level.WARNING, e.getMessage(), e);
            last = 0;
        }
        finally
        {
            if (br != null) br.close();
            if (is != null) is.close();
        }

        return last;
    }

    private static String readLocation(File file)
        throws Exception
    {
        InputStream is = null;
        BufferedReader br = null;
        try
        {
            is = new FileInputStream(file);
            br = new BufferedReader(new InputStreamReader(is));
            return br.readLine();
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: readLastModified :");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        finally
        {
            if (br != null) br.close();
            if (is != null) is.close();
        }
        return "";
    }

    private synchronized static boolean isUpdateRequired
        (String hosted, String local, Version v)
            throws Exception
    {
        try
        {
            int idx = local.lastIndexOf("/");
            int idxJar = local.lastIndexOf(".jar");
            if (idx != -1 && idxJar > idx)
            {
                String jarLoc = local.substring(idx + 1, idxJar + 4);
                int idxQ = hosted.indexOf("?");
                if (idxQ != -1)
                {
                    String version = hosted.substring(idxQ + 1);
                    hosted = hosted.substring(0, idxQ);
                    if (jarLoc.equalsIgnoreCase(hosted))
                    {
                        m_logger.info("FOUND MATCH with " + hosted + ", " +
                        		"now check if update is required");
                        String[] tokens = version.split("=");
                        if (tokens.length == 2)
                        {
                            if (tokens[0].equalsIgnoreCase("version"))
                            {
                                Version cmp = null;
                                try
                                {
                                    cmp = Version.parseVersion(tokens[1]);
                                }
                                catch(NumberFormatException nfe)
                                {
                                    m_logger.log(Level.WARNING,
                                        "Version # is not valid -> " + tokens[1]);
                                    return false;
                                }

                                if (cmp != null && v != null)
                                {
                                    boolean areEqual = (cmp.compareTo(v) != 0);
                                    if (areEqual)
                                    {
                                        m_logger.log(Level.FINE,
                                            "YES We need to update " + jarLoc +
                                                " to version " + tokens[1] +
                                                " Current version is " + v);
                                    }
                                    else
                                    {
                                        m_logger.log(Level.FINE,
                                            "NO We don't need to update " +
                                                jarLoc + " version is up to date");
                                    }
                                    return areEqual;
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: isUpdateRequired :");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }

        return false;
    }

    private synchronized static String getLatestBundleVersion(String[] fs)
    {
        try
        {
            int store = -1;
            double max = -1;
            if (fs.length == 1)
            {
                return fs[0];
            }
            for(int i=0; i < fs.length; i++)
            {
                String f = fs[i];
                if (f.indexOf("version") == 0)
                {
                    String v = f.substring(7);
                    Double d = Double.valueOf(v);
                    if (d > max)
                    {
                        max = d;
                        store = i;
                    }
                }
            }
            if (store != -1)
            {
                return fs[store];
            }
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: getLatestBundleVersion :");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return "";
    }

    private synchronized static Version getVersion(String bundle)
    {
        ZipFileX zipFile = null;
        Map<String, String> result = new HashMap<String,String>(10);
        try
        {
            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("version");
                }
            };

            File f = new File(bundle);
            String [] fs = f.list(filter);
            String jar = null;
            if (fs.length > 0)
            {
                String fJarVersion = getLatestBundleVersion(fs);
                if (f.length() > 0)
                {
                    jar = bundle + SEPARATOR + fJarVersion + SEPARATOR + "bundle.jar";
                }
            }
            if (jar == null || jar.length() == 0)
            {
                return null;
            }

            File ftmp = new File(jar);
            if (!ftmp.exists())
            {
                return null;
            }

            // m_logger.log(Level.FINE, "The Jar file is using " + jar);

            // Open bundle JAR file.
            zipFile = new ZipFileX(jar);
            ZipEntry entry = zipFile.getEntry("META-INF/MANIFEST.MF");

            byte[] bytes = null;
            int size = (int) entry.getSize();
            bytes = new byte[size];

            /*
             *  Now read in the manifest in one go into the bytes array.
             *  The InputStream is already
             */
            InputStream is = null;
            try
            {
                is = zipFile.getInputStream(entry);
                int i = is.read(bytes);
                while (i < size)
                {
                    i += is.read(bytes, i, bytes.length - i);
                }
            }
            finally
            {
                is.close();
            }

            String key = null;
            int last = 0;
            int current = 0;
            for (int i = 0; i < size; i++)
            {
                /*
                 *  skip \r and \n if it is follows by another \n
                 *  (we catch the blank line case in the next iteration)
                 */
                if (bytes[i] == '\r')
                {
                    if ((i + 1 < size) && (bytes[i + 1] == '\n'))
                    {
                        continue;
                    }
                }
                if (bytes[i] == '\n')
                {
                    if ((i + 1 < size) && (bytes[i + 1] == ' '))
                    {
                        i++;
                        continue;
                    }
                }
                /*
                 *  If we don't have a key yet and see the first :
                 *  we parse it as the key and skip the :<blank>
                 *  that follows it.
                 */
                if ((key == null) && (bytes[i] == ':'))
                {
                    key = new String(bytes, last, (current - last), "UTF-8");
                    if ((i + 1 < size) && (bytes[i + 1] == ' '))
                    {
                        last = current + 1;
                        continue;
                    }
                    else
                    {
                        throw new Exception(
                            "Manifest error: Missing space separator - " +
                                key);
                    }
                }
                // if we are at the end of a line
                if (bytes[i] == '\n')
                {
                    // and it is a blank line stop parsing (main attributes are done)
                    if ((last == current) && (key == null))
                    {
                        break;
                    }
                    // Otherwise, parse the value and add it to the map (we throw an
                    // exception if we don't have a key or the key already exist.
                    String value = new String(bytes, last, (current - last), "UTF-8");
                    if (key == null)
                    {
                        throw new Exception
                            ("Manifest error: Missing attribute name - " +
                                value);
                    }
                    else if (result.put(key, value) != null)
                    {
                        throw new Exception
                            ("Manifest error: Duplicate attribute name - " +
                                key);
                    }
                    if (key.equalsIgnoreCase("Bundle-Version"))
                    {
                        /*
                         * Found bundle, return prematurely for now
                         */
                        m_logger.log(Level.FINEST,
                            "Found version " + value + " for jar " + jar);
                        result.clear();
                        return Version.parseVersion(value);
                    }
                    last = current;
                    key = null;
                }
                else
                {
                    // write back the byte if it needs to be included in the key or the value.
                    bytes[current++] = bytes[i];
                }
            }
            if (result.get("Bundle-Version") != null)
            {
                String v = result.get("Bundle-Version");
                return Version.parseVersion(v);
            }
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: getVersion :");
            m_logger.log(Level.SEVERE, "Error while trying to read version", e);
        }
        return null;
    }

    private synchronized static File[] getOustedJarList(String [] hostedBundles)
        throws Exception
    {
        File [] flagBundles = new File[0];
        try
        {
            File felix = getFelixDir();
            if (felix.exists() && felix.isDirectory())
            {
                File [] fBundles = felix.listFiles();
                flagBundles = new File[fBundles.length];
                int counter = 0;
                for (int i=0; i < fBundles.length; i++)
                {
                    File bundle = fBundles[i];
                    if (bundle.isDirectory())
                    {
                        if (bundle.getName().startsWith("$") &&
                            bundle.getName().endsWith("$"))
                        {
                            continue;
                        }

                        File fLocation = new File(bundle.getPath() +
                            SEPARATOR + "bundle.location");
                        File fLastModified = new File(bundle.getPath() +
                            SEPARATOR + "bundle.lastmodified");
                        if (fLocation.exists() && fLastModified.exists())
                        {
                            String localLocation = readLocation(fLocation);
                            /*
                             * The currently install version
                             */
                            Version v = getVersion(bundle.getAbsolutePath());
                            String localNoParam = localLocation;
                            int idxQ = localLocation.indexOf("?");
                            if (idxQ != -1)
                            {
                                //localNoParam = localNoParam.substring(0, idxQ);
                            }
                            if (v != null)
                            {
                                m_logger.log(Level.INFO,
                                    "Installed version " +
                                        v.toString() + " for bundle " +
                                            localNoParam);
                            }
                            else
                            {
                                m_logger.log(Level.INFO,
                                    "Could not identify installed version for " +
                                        localNoParam);
                            }

                            for (int j=0; j < hostedBundles.length; j++)
                            {
                                String jar = hostedBundles[j];
                                boolean update = isUpdateRequired(jar, localLocation, v);
                                if (update)
                                {
                                    flagBundles[counter++] = bundle;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            m_logger.log(Level.SEVERE, "Exception :: getOustedJarList :");
            m_logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return flagBundles;
    }
}
