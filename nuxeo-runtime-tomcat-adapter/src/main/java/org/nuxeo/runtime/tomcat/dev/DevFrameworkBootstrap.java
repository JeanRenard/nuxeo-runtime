/*
 * (C) Copyright 2006-2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     bstefanescu
 */
package org.nuxeo.runtime.tomcat.dev;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.nuxeo.osgi.application.FrameworkBootstrap;
import org.nuxeo.osgi.application.MutableClassLoader;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 * 
 */
public class DevFrameworkBootstrap extends FrameworkBootstrap implements
        DevBundlesManager {

    protected File devBundlesFile;

    protected DevBundle[] devBundles;

    protected Timer bundlesCheck;

    protected long lastModified = 0;

    protected ReloadServiceInvoker reloadServiceInvoker;

    public DevFrameworkBootstrap(ClassLoader cl, File home) throws IOException {
        super(cl, home);
    }

    public DevFrameworkBootstrap(MutableClassLoader cl, File home)
            throws IOException {
        super(cl, home);
        devBundlesFile = new File(home, "dev.bundles");
    }

    @Override
    public void start() throws Exception {
        // check if we have dev. bundles or libs to deploy and add them to the
        // classpath
        preloadDevBundles();
        // start the framework
        super.start();
        reloadServiceInvoker = new ReloadServiceInvoker((ClassLoader) loader);
        writeComponentIndex();
        postloadDevBundles(); // start dev bundles if any
        installLoaderTimer();
    }

    public void installLoaderTimer() {
        String installReloadTimerOption = (String) env.get(INSTALL_RELOAD_TIMER);
        if (installReloadTimerOption == null
                || Boolean.parseBoolean(installReloadTimerOption) == Boolean.FALSE) {
            return;
        }
        // start reload timer
        bundlesCheck = new Timer("Dev Bundles Loader");
        bundlesCheck.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    loadDevBundles();
                } catch (Throwable t) {
                    log.error("Error running dev mode timer", t);
                }
            }
        }, 2000, 2000);
    }

    @Override
    public void stop() throws Exception {
        if (bundlesCheck != null) {
            bundlesCheck.cancel();
            bundlesCheck = null;
        }
        super.stop();
    }

    public String getDevBundlesLocation() {
        return devBundlesFile.getAbsolutePath();
    }

    /**
     * Load the development bundles and libs if any in the classpath before
     * starting the framework.
     */
    protected void preloadDevBundles() throws IOException {
        if (devBundlesFile.isFile()) {
            lastModified = devBundlesFile.lastModified();
            devBundles = DevBundle.parseDevBundleLines(new FileInputStream(
                    devBundlesFile));
            if (devBundles.length == 0) {
                devBundles = null;
                return;
            }
            // clear dev classloader
            NuxeoDevWebappClassLoader devLoader = (NuxeoDevWebappClassLoader) loader;
            devLoader.clear();
            URL[] urls = new URL[devBundles.length];
            for (int i = 0; i < devBundles.length; i++) {
                urls[i] = devBundles[i].url();
            }
            devLoader.createLocalClassLoader(urls);
        }
    }

    protected void postloadDevBundles() throws Exception {
        if (devBundles != null) {
            reloadServiceInvoker.hotDeployBundles(devBundles);
            reloadServiceInvoker.flush();
        }
    }

    public void loadDevBundles() {
        long tm = devBundlesFile.lastModified();
        if (lastModified >= tm) {
            return;
        }
        lastModified = tm;
        try {
            reloadDevBundles(DevBundle.parseDevBundleLines(new FileInputStream(
                    devBundlesFile)));
        } catch (Exception e) {
            log.error("Faied to deploy dev bundles", e);
        }
    }

    public void resetDevBundles(String path) {
        devBundlesFile = new File(path);
        lastModified = 0;
        loadDevBundles();
    }

    public DevBundle[] getDevBundles() {
        return devBundles;
    }

    protected synchronized void reloadDevBundles(DevBundle[] bundles)
            throws Exception {

        // un-deploy previous bundles
        if (devBundles != null) {
            reloadServiceInvoker.hotUndeployBundles(devBundles);
        }
        devBundles = bundles;

        // flush and reset class loader
        NuxeoDevWebappClassLoader devLoader = (NuxeoDevWebappClassLoader) loader;
        devLoader.clear();
        System.gc();
        List<URL> jarUrls = new ArrayList<URL>();
        List<File> seamDirs = new ArrayList<File>();
        List<File> resourceBundleFragments = new ArrayList<File>();
        for (DevBundle bundle : bundles) {
            if (bundle.devBundleType.isJar) {
                jarUrls.add(bundle.url());
            } else if (bundle.devBundleType == DevBundleType.Seam) {
                seamDirs.add(bundle.file());
            } else if (bundle.devBundleType == DevBundleType.ResourceBundleFragment) {
                resourceBundleFragments.add(bundle.file());
            }
        }
        devLoader.createLocalClassLoader(jarUrls.toArray(new URL[jarUrls.size()]));
        devLoader.installSeamClasses(seamDirs.toArray(new File[seamDirs.size()]));
        devLoader.installResourceBundleFragments(resourceBundleFragments);
        // deploy last bundles
        if (devBundles != null) {
            reloadServiceInvoker.hotDeployBundles(devBundles);
        }
        reloadServiceInvoker.flush();
    }

    public void writeComponentIndex() {
        File file = new File(home.getParentFile(), "sdk");
        file.mkdirs();
        file = new File(file, "components.xml");
        // if (file.isFile()) {
        // return;
        // }
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            Method m = getClassLoader().loadClass(
                    "org.nuxeo.runtime.model.impl.ComponentRegistrySerializer").getMethod(
                    "toXML", Writer.class);
            m.invoke(null, writer);
        } catch (Throwable t) {
            // ignore
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

}
