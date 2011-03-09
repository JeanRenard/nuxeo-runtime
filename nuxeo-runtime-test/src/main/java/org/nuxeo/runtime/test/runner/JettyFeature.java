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
package org.nuxeo.runtime.test.runner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.runtime.test.WorkingDirectoryConfigurator;


/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
@Deploy("org.nuxeo.runtime.jetty")
@Features(RuntimeFeature.class)
public class JettyFeature extends SimpleFeature implements WorkingDirectoryConfigurator {

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        Jetty jetty = FeaturesRunner.getScanner().getFirstAnnotation(runner.getTargetTestClass(), Jetty.class);
        if (jetty == null) {
            jetty = Defaults.of(Jetty.class);
        }
        configureJetty(jetty);

        runner.getFeature(RuntimeFeature.class).getHarness().addWorkingDirectoryConfigurator(this);
    }

    protected void configureJetty(Jetty jetty){
        int p = jetty.port();
        try {
            String s = System.getenv("JETTY_PORT");
            if ( s != null) {
                p = Integer.parseInt(s);
            }
        } catch (Exception e){
            // do nothing ; the jetty.port
        }
        if (p > 0) {
            System.setProperty("jetty.port", Integer.toString(p));
        }

        String host = System.getenv("JETTY_HOST");
        if ( host == null ){
            host = jetty.host();
        }
        if (host.length() > 0) {
            System.setProperty("jetty.host", host);
        }

        String config = System.getenv("JETTY_CONFIG");
        if ( config == null ){
            config = jetty.config();
        }
        if (config.length() > 0) {
            System.setProperty("org.nuxeo.jetty.config", config);
        }
    }


    @Override
    public void configure(RuntimeHarness harness, File workingDir) throws IOException {
        File dest = new File(workingDir, "config");
        dest.mkdirs();

        InputStream in = getResource("jetty/default-web.xml").openStream();
        dest = new File(workingDir + "/config", "default-web.xml");
        try {
            FileUtils.copyToFile(in, dest);
        } finally {
            in.close();
        }

        in = getResource("jetty/jetty.xml").openStream();
        dest = new File(workingDir + "/config", "jetty.xml");
        try {
            FileUtils.copyToFile(in, dest);
        } finally {
            in.close();
        }
    }

    private static URL getResource(String resource) {
        //return Thread.currentThread().getContextClassLoader().getResource(resource);
        return Jetty.class.getClassLoader().getResource(resource);
    }

}
