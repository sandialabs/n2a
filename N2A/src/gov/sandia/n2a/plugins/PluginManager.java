/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;


/**
 * This class is the heart of an extremely simplified plug-in
 * framework.  The goal of this framework is to enable small
 * to medium sized Java applications to leverage the benefits
 * of a plug-in architecture without having to endure the
 * complexity and size of a more standard and mature framework
 * like OSGi or JPF, both of which employ a declarative (XML-
 * based) representation of plug-ins that provides great
 * lazy-loading and interoperability characteristics.
 *
 * This plug-in framework ignores the benefits of a markup
 * language to describe plug-ins and instead falls back onto
 * basic interface/implementation mechanisms to achieve a
 * similar goal.  However, it does employ the similar,
 * high-level concept of "extension points" and "extensions".
 *
 * A plug-in is any class implementing the Plugin interface.
 * A plug-in thus declares both the extension points and
 * extensions that it contains.  An extension is a piece of
 * code that is supposed to provide functionality to the
 * platform or another plug-in, and an extension point is
 * the contract by which a plug-in can provide that
 * functionality.
 *
 * Thus, an extension point is created simply by creating
 * 1) a Java interface that extends ExtensionPoint, or
 * 2) a Java class that implements ExtensionPoint.  An
 * extension thus is any object that is an instance of either
 * such an interface or such a class.  In other words, it is
 * an object that is an instance of ExtensionPoint.  When
 * plug-in objects are registered upon application start-up,
 * extensions are mapped to the extension points they connect
 * to, and you can always look up the extensions for a given
 * extension point using the PluginManager class.
 *
 * What it means to be an instance of a plug-in or an extension
 * is fairly clear.  They are objects that inherit from Plugin
 * or ExtensionPoint respectively.  But what is an instance of
 * an extension point?  This framework defines that as an
 * object of type Class<? extends ExtensionPoint>.  This means
 * That if you have an extension point:
 *
 *     public interface Function extends ExtensionPoint {
 *
 *     }
 *
 * Then the extension point is the object Function.class.  This
 * is important because when you implement a plug-in, you have
 * to provide a list of extension points in this form.  For
 * example:
 *
 *     @Override
 *     public Class<? extends ExtensionPoint>[] getExtensionPoints() {
 *         return new Class[] {
 *             Function.class,
 *             Simulator.class,
 *             View.class,
 *             Editor.class
 *         };
 *     }
 *
 * This required method essentially declares which classes define
 * extension point "contracts", and thus are themselves considered
 * extension points.  The type Class<? extends ExtensionPoint> is
 * not the cleanest syntax for this but it was done that way to
 * be explicit about what it means to be an extension point.  This
 * method can return null to indicate the plug-in has no published
 * extension points.
 *
 * To initialize the plug-in framework, you may call the initialize
 * method on this class.  It takes various parameters that should
 * be common to initializing a plug-in framework upon startup of
 * an application.
 *
 * @author dtrumbo
 */

public class PluginManager
{
    protected static Map<String, Plugin>                          globalPlugins   = new LinkedHashMap<String, Plugin> ();                           // maps plugin name to instance
    protected static Map<String, Class<? extends ExtensionPoint>> globalExtPoints = new LinkedHashMap<String, Class<? extends ExtensionPoint>> ();  // maps full class name to class
    protected static Map<String, ExtensionPoint>                  globalExts      = new LinkedHashMap<String, ExtensionPoint> ();                   // maps full class name to instance

    protected static Map<String, List<Class<? extends ExtensionPoint>>> ownedPluginExtPoints = new LinkedHashMap<String, List<Class<? extends ExtensionPoint>>> ();
    protected static Map<String, List<ExtensionPoint>>                  ownedPluginExts      = new LinkedHashMap<String, List<ExtensionPoint>> ();
    protected static Map<String, List<ExtensionPoint>>                  ownedExtPointExts    = new LinkedHashMap<String, List<ExtensionPoint>> ();


    // ID -> Obj

    public static Plugin getPluginById (String pluginId)
    {
        return globalPlugins.get (pluginId);
    }

    public static Class<? extends ExtensionPoint> getExtensionPointById (String extPointId)
    {
        return globalExtPoints.get (extPointId);
    }

    public static ExtensionPoint getExtensionById (String extId)
    {
        return globalExts.get (extId);
    }


    // Obj -> ID

    public static String getPluginId (Plugin plugin)
    {
        return plugin.getClass ().getName ();
    }

    public static String getExtensionPointId (Class<? extends ExtensionPoint> extPoint)
    {
        return extPoint.getName ();
    }

    public static String getExtensionId (ExtensionPoint ext)
    {
        return ext.getClass ().getName ();
    }


    // Ownership

    public static List<Class<? extends ExtensionPoint>> getExtensionPointsInPlugin (Plugin plugin)
    {
        String pluginId = getPluginId (plugin);
        List<Class<? extends ExtensionPoint>> extPoints = ownedPluginExtPoints.get (pluginId);
        if (extPoints == null) return new ArrayList<Class<? extends ExtensionPoint>> ();
        return Collections.unmodifiableList (extPoints);
    }

    public static List<ExtensionPoint> getExtensionsInPlugin (Plugin plugin)
    {
        String pluginId = getPluginId (plugin);
        List<ExtensionPoint> exts = ownedPluginExts.get (pluginId);
        if (exts == null) return new ArrayList<ExtensionPoint> ();
        return Collections.unmodifiableList (exts);
    }

    public static List<ExtensionPoint> getExtensionsForPoint (Class<? extends ExtensionPoint> extPoint)
    {
        String extPointId = getExtensionPointId (extPoint);
        List<ExtensionPoint> exts = ownedExtPointExts.get (extPointId);
        if (exts == null) return new ArrayList<ExtensionPoint> ();
        return Collections.unmodifiableList (exts);
    }

    public static Class<? extends ExtensionPoint> getPointForExtension (ExtensionPoint ext)
    {
        try
        {
            return findOneExtPointClass (ext.getClass (), false);
        }
        catch (Exception e)
        {
            // Should never happen since validation should happen at plug-in load time.
            throw new RuntimeException ("Invalid extension '" + getExtensionId (ext) + "'.", e);
        }
    }


    // Initialization

    public static void initialize (Plugin platformPlugin, List<String> loadFromMemByName, List<Path> loadFromPluginDirs)
    {
        // Load an initial platform plug-in if provided.
        if (platformPlugin != null)
        {
            try
            {
                loadFromMemoryByName (platformPlugin.getClass ().getName ());
            }
            catch (Exception e)
            {
                System.err.println ("Error loading platform:");
                e.printStackTrace (System.err);
            }
        }

        // Create temporary class path for SPI
        List<URL> urls = new ArrayList<URL> ();
        if (loadFromPluginDirs != null)
        {
            for (Path dir : loadFromPluginDirs)
            {
                if (! Files.exists (dir))
                {
                    System.err.println ("Plugin dir missing: " + dir);
                    continue;
                }

                try
                {
                    Files.walkFileTree (dir, new SimpleFileVisitor<Path> ()
                    {
                        public FileVisitResult visitFile (Path file, BasicFileAttributes attrs) throws IOException
                        {
                            if (file.getFileName ().toString ().toLowerCase ().endsWith (".jar"))
                            {
                                try
                                {
                                    urls.add (file.toUri ().toURL ());
                                }
                                catch (MalformedURLException e) {}  // If the file exists, this exception should never happen.
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                catch (IOException e)
                {
                    System.err.println ("Exception will scanning plugin dir: " + dir);
                    e.printStackTrace (System.err);
                }
            }
        }
        URLClassLoader tempLoader = new URLClassLoader (urls.toArray (new URL[urls.size ()]));

        // Check for any plugins provided by SPI.
        ServiceLoader<Plugin> loader = ServiceLoader.load (Plugin.class, tempLoader);
        Iterator<Plugin> pit = loader.iterator ();
        while (pit.hasNext ())
        {
            Plugin p = pit.next ();
            try
            {
                load (p);
            }
            catch (Exception e)
            {
                String className = p.getClass ().getName ();
                System.err.println ("Error loading SPI-provided plugin: " + className);
                e.printStackTrace (System.err);
            }
        }

        // Load explicitly-named classes. These must be known to the JVM's class loader.
        if (loadFromMemByName != null)
        {
            for (String className : loadFromMemByName)
            {
                try
                {
                    loadFromMemoryByName (className);
                }
                catch (Exception e)
                {
                    System.err.println ("Error loading named plugin: " + className);
                    e.printStackTrace (System.err);
                }
            }
        }

        try
        {
            validateExtPoints ();
        }
        catch (Exception e)
        {
            System.err.println ("Error validating extension points:");
            e.printStackTrace (System.err);
        }

        for (String pName : globalPlugins.keySet ())
        {
            try
            {
                Plugin plugin = globalPlugins.get (pName);
                plugin.start ();
            }
            catch (Exception e)
            {
                System.err.println ("Error starting plugin: " + pName);
                e.printStackTrace (System.err);
            }
        }
    }


    /////////////
    // LOADING //
    /////////////

    public static void loadFromMemoryByName (String pluginClassName) throws Exception
    {
        // Attempt to instantiate the plug-in object from the given fully-qualified class name.
        Plugin plugin;
        try
        {
            Class<?> pluginClass = Class.forName (pluginClassName);
            Constructor<?> ctor = pluginClass.getConstructor (new Class<?>[0]);
            plugin = (Plugin) ctor.newInstance (new Object[0]);
        }
        catch (ClassNotFoundException e)
        {
            throw new Exception ("Could not find plug-in class '" + pluginClassName + "'.", e);
        }
        catch (NoSuchMethodException e)
        {
            throw new Exception ("Plug-in class '" + pluginClassName + "' does not have a default constructor.", e);
        }
        catch (InstantiationException e)
        {
            throw new Exception ("Plug-in class '" + pluginClassName + "' must not be abstract.", e);
        }
        catch (ClassCastException e)
        {
            throw new Exception ("Plug-in class '" + pluginClassName + "' does not implement '" + Plugin.class.getName() + "'.", e);
        }
        catch (Exception e)
        {
            Throwable cause = e.getCause ();
            if ((cause instanceof ClassNotFoundException)  ||  (cause instanceof NoClassDefFoundError))
            {
                throw new RuntimeException ("Plug-in object '" + pluginClassName + "' failed to be instantiated. The plug-in does not appear to have all its required classes loaded.", e);
            }
            throw new RuntimeException ("Plug-in object '" + pluginClassName + "' failed to be instantiated.", e);
        }

        load (plugin);
    }

    public static void load (Plugin plugin) throws Exception
    {
        // Validate the plug-in object.
        String pluginId = getPluginId(plugin);
        if (getPluginById (pluginId) != null)
        {
            throw new Exception ("A plug-in with the ID '" + pluginId + "' has already been loaded.");
        }
        if (plugin.getName ().isEmpty ()  ||  plugin.getVersion ().isEmpty ())
        {
            String className = plugin.getClass ().getName ();
            throw new Exception ("Plug-in '" + className + "' contains invalid values for name or version.");
        }

        // Record what extension points are in this plug-in.
        Class<? extends ExtensionPoint>[] eps = plugin.getExtensionPoints ();
        List<Class<? extends ExtensionPoint>> myExtPoints = new ArrayList<Class<? extends ExtensionPoint>> ();

        if (eps != null)
        {
            for (Class<? extends ExtensionPoint> ep : eps)
            {
                // Validate the extension point classes provided by the plug-in.
                // Extension point classes should only inherit from a single
                // interface that extends ExtensionPoint for clarity's sake.
                Class<? extends ExtensionPoint> foundExtPoint = findOneExtPointClass (ep, true);

                // Make sure this extension point class hasn't already been
                // loaded by another plug-in.
                String extPointId = getExtensionPointId (foundExtPoint);
                if (getExtensionPointById (extPointId) != null)
                {
                    throw new Exception ("An extension point with the ID '" + extPointId + "' has already been loaded.");
                }

                // Remember this valid extension point.
                myExtPoints.add (foundExtPoint);
            }
        }

        // Record what extensions are in this plug-in.
        ExtensionPoint[] es = plugin.getExtensions ();
        List<ExtensionPoint> myExts = new ArrayList<ExtensionPoint>();

        if (es != null)
        {
            for (ExtensionPoint e : es)
            {
                // Validate the extension classes provided by the plug-in.
                // Extension classes should only implement a single
                // interface that extends ExtensionPoint for clarity's sake.
                findOneExtPointClass (e.getClass (), false);

                // Make sure this extension point class hasn't already been
                // loaded by another plug-in.
                String extId = getExtensionId (e);
                if (getExtensionById (extId) != null)
                {
                    throw new Exception ("An extension with the ID '" + extId + "' has already been loaded.");
                }

                // Remember this valid extension.
                myExts.add (e);
            }
        }

        // Plug-in class and instance are considered good at this point.

        // Remember the plug-in (global).
        globalPlugins.put (pluginId, plugin);

        // Remember all the extension points in this plug-in (global).
        for (Class<? extends ExtensionPoint> extPoint : myExtPoints)
        {
            String extPointId = getExtensionPointId (extPoint);
            globalExtPoints.put (extPointId, extPoint);
            getOwnedExtPointExts (extPoint);
        }

        // Remember all the extensions in this plug-in (global).
        for (ExtensionPoint ext : myExts)
        {
            String extId = getExtensionId (ext);
            globalExts.put (extId, ext);

            // Remember the extensions for the given extension points (ownership).
            Class<? extends ExtensionPoint> foundExtPoint = findOneExtPointClass (ext.getClass (), false);
            List<ExtensionPoint> myExtPointExts = getOwnedExtPointExts (foundExtPoint);
            myExtPointExts.add (ext);
        }

        // Remember the extension points and extensions for this plug-in (ownership).
        ownedPluginExtPoints.put (pluginId, myExtPoints);
        ownedPluginExts.put (pluginId, myExts);
    }

    protected static List<ExtensionPoint> getOwnedExtPointExts (Class<? extends ExtensionPoint> extPoints)
    {
        String extPointId = getExtensionPointId (extPoints);
        List<ExtensionPoint> myExtPointExts = ownedExtPointExts.get (extPointId);
        if (myExtPointExts == null)
        {
            myExtPointExts = new ArrayList<ExtensionPoint> ();
            ownedExtPointExts.put (extPointId, myExtPointExts);
        }
        return myExtPointExts;
    }

    public static void validateExtPoints () throws Exception
    {
        for (String extPointId : ownedExtPointExts.keySet ())
        {
            if (! globalExtPoints.keySet ().contains (extPointId))
            {
                String s = "";
                for (ExtensionPoint ext : ownedExtPointExts.get (extPointId))
                {
                    s += ", " + ext.getClass ().getName ();
                }
                s = s.substring (2);  // to remove the leading ", "
                throw new Exception ("The extension point '" + extPointId + "' has not been declared by any plug-in, but it is used by extension classes: " + s);
            }
        }
    }

    /**
        Determines which extension point the given class belongs to.
        This is defined as an immediate descendant of ExtensionPoint.
    **/
    protected static Class<? extends ExtensionPoint> findOneExtPointClass (Class<? extends ExtensionPoint> clazz, boolean isExtPoint) throws Exception
    {
        String objName = isExtPoint ? "extension point" : "extension";
        String actionName = isExtPoint ? "extend" : "implement";

        List<Class<? extends ExtensionPoint>> foundExtPoints = findExtPoints (clazz);
        if (foundExtPoints.size () == 0)
        {
            throw new Exception ("The " + objName + " class '" + clazz.getName () + "' does not " + actionName + " any extension point interface.");
        }
        else if (foundExtPoints.size () > 1)
        {
            String s = "";
            for (Class<?> foundExtPoint : foundExtPoints)
            {
                s += ", " + foundExtPoint.getName ();
            }
            s = s.substring (2);
            throw new Exception ("The " + objName + " class '" + clazz.getName () + "' " + actionName + "s more than one extension point interface (" + s + ").");
        }

        return foundExtPoints.get (0);
    }

    /**
        Walks up the inheritance hierarchy of the given class and collects all ancestors which
        are direct descendants of ExtensionPoint. Ideally, the return set will have a single item.
    **/
    protected static List<Class<? extends ExtensionPoint>> findExtPoints (Class<? extends ExtensionPoint> clazz)
    {
        List<Class<? extends ExtensionPoint>> result = new ArrayList<Class<? extends ExtensionPoint>>();
        findExtPoints (clazz, result);
        return result;
    }

    /**
        Subroutine of findExtPoints(class).
    **/
    @SuppressWarnings("unchecked")
    protected static void findExtPoints (Class<?> clazz, List<Class<? extends ExtensionPoint>> result)
    {
        Class<?> clazzParent = clazz.getSuperclass ();
        if (clazzParent != null)
        {
            if (clazzParent.equals (ExtensionPoint.class))
            {
                result.add ((Class<? extends ExtensionPoint>) clazz);
            }
            else
            {
                findExtPoints (clazzParent, result);
            }
        }

        Class<?>[] clazzImpl = clazz.getInterfaces ();
        if (clazzImpl != null)
        {
            for (Class<?> impl : clazzImpl)
            {
                if (impl.equals (ExtensionPoint.class))
                {
                    result.add ((Class<? extends ExtensionPoint>) clazz);
                }
                else
                {
                    findExtPoints (impl, result);
                }
            }
        }
    }

    public static void list ()
    {
        for (String pluginId : globalPlugins.keySet ())
        {
            Plugin plugin = globalPlugins.get (pluginId);
            System.out.println ("Plugin => " + pluginId + " / " + plugin.getName () + " / " + plugin.getVersion ());
            for (Class<? extends ExtensionPoint> extPoint : ownedPluginExtPoints.get (pluginId))
            {
                System.out.println ("    ExtPoint => " + getExtensionPointId (extPoint));
            }
            for (ExtensionPoint ext : ownedPluginExts.get (pluginId))
            {
                System.out.println ("    Ext => " + getExtensionId (ext));
                System.out.println ("        Point => " + getExtensionPointId (getPointForExtension (ext)));
            }
        }
    }
}
