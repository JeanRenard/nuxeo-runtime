/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.runtime.management.metrics;

import java.io.File;
import java.io.IOException;

import javax.management.MXBean;

@MXBean
public interface MetricSerializerMBean {

    int getCount();

    long getLastUsage();

    void closeOutput() throws IOException;

    void resetOutput() throws IOException;

    void resetOutput(String path) throws IOException;

    void flushOuput() throws IOException;

    File getOutputFile();

}