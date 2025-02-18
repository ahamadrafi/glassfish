/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.appserv.server.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * This class provides static methods to make accessible the version as well as
 * the individual parts that make up the version
 */
public class Version {

    private static final String INSTALL_ROOT_PROP_NAME = "com.sun.aas.installRoot";
    private static final String PRODUCT_NAME_KEY = "product_name";
    private static final String ABBREV_PRODUCT_NAME_KEY = "abbrev_product_name";
    private static final String MAJOR_VERSION_KEY = "major_version";
    private static final String MINOR_VERSION_KEY = "minor_version";
    private static final String UPDATE_VERSION_KEY = "update_version";
    private static final String BUILD_ID_KEY = "build_id";
    private static final String VERSION_PREFIX_KEY = "version_prefix";
    private static final String VERSION_SUFFIX_KEY = "version_suffix";
    private static final String BASED_ON_KEY = "based_on";
    private static final String DEFAULT_DOMAIN_TEMPLATE_NAME = "default_domain_template";
    private static final String DEFAULT_DOMAIN_TEMPLATE_JAR = "nucleus-domain.jar";
    private static final String ADMIN_CLIENT_COMMAND_NAME_KEY = "admin_client_command_name";
    private static final String INITIAL_ADMIN_GROUPS_KEY = "initial_admin_user_groups";
    private static List<Properties> versionProps = new ArrayList<>();
    private static Map<String,Properties> versionPropsMap = new HashMap<>();
    private static Properties versionProp = getVersionProp();

    private static Properties getVersionProp() {
        String installRoot = System.getProperty(INSTALL_ROOT_PROP_NAME);
        if (installRoot != null) {
            File ir = new File(installRoot);
            File bd = new File(new File(ir, "config"), "branding");
            if (bd.isDirectory()) {
                for (File f : bd.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.getName().endsWith(".properties") && f.canRead();
                    }
                })) {
                    FileReader fr = null;
                    try {
                        fr = new FileReader(f);
                        Properties p = new Properties();
                        p.load(fr);
                        versionProps.add(p);
                        String apn = p.getProperty(ABBREV_PRODUCT_NAME_KEY);
                        if (apn != null) {
                            versionPropsMap.put(apn, p);
                        }
                        fr.close();
                    } catch (IOException ex) {
                        // ignore files that cannot be read
                    } finally {
                        if (fr != null) {
                            try {
                                fr.close();
                            } catch (IOException ex) {
                                // nothing to do
                            }
                        }
                    }
                }
            }
            // sort the list based on the based-on property.  If a is based on b,
            // then a is earlier then b in the list.
            Collections.sort(versionProps, new Comparator<Properties>() {
                @Override
                public int compare(Properties p1, Properties p2) {
                    String abp1 = p1.getProperty(ABBREV_PRODUCT_NAME_KEY);
                    String bo1 = p1.getProperty(BASED_ON_KEY);
                    String abp2 = p2.getProperty(ABBREV_PRODUCT_NAME_KEY);
                    String bo2 = p2.getProperty(BASED_ON_KEY);
                    if (bo1 != null && abp2 != null && bo1.contains(abp2)) {
                        return -1;
                    }
                    if (bo2 != null && abp1 != null && bo2.contains(abp1)) {
                        return 1;
                    }
                    return 0;
                }
            });

            // save the first element in the list for later use
            if (versionProps.size() > 0) {
                return versionProps.get(0);
            }
        } else {
            System.out.println("installRoot is null");
        }
        return null;
    }

    /**
     * Returns version
     */
    public static String getVersion() {
        StringBuilder sb = new StringBuilder(getProductName());
        sb.append(" ").append(getVersionPrefix());
        sb.append(" ").append(getVersionNumber());
        sb.append(" ").append(getVersionSuffix());
        return sb.toString();
    }

    /**
     * Return major_version [. minor_version [. update_version]]
     */
    public static String getVersionNumber() {
        // construct version number
        String maj = getMajorVersion();
        String min = getMinorVersion();
        String upd = getUpdateVersion();
        String v;
        try {
            if (min != null && min.length() > 0 && Integer.parseInt(min) >= 0) {
                if (upd != null && upd.length() > 0 && Integer.parseInt(upd) >= 0) {
                    v = maj + "." + min + "." + upd;
                } else {
                    v = maj + "." + min;
                }
            } else {
                if (upd != null && upd.length() > 0 && Integer.parseInt(upd) >= 0) {
                    v = maj + ".0." + upd;
                } else {
                    v = maj;
                }
            }
        } catch (NumberFormatException nfe) {
            v = maj;
        }
        return v;
    }

    /**
     * Returns full version including build id
     */
    public static String getFullVersion() {
        return (getVersion() + " (build " + getBuildVersion() + ")");
    }

    /**
     * Returns abbreviated version.
     */
    public static String getAbbreviatedVersion() {
        return getMajorVersion();
    }

    /**
     * Returns Major version
     */
    public static String getMajorVersion() {
        return getProperty(MAJOR_VERSION_KEY, "0");
    }

    /**
     * Returns Minor version
     */
    public static String getMinorVersion() {
        return getProperty(MINOR_VERSION_KEY, "0");
    }

    /**
     * Returns Update version
     */
    public static String getUpdateVersion() {
        return getProperty(UPDATE_VERSION_KEY, "0");
    }

    /**
     * Returns Build version
     */
    public static String getBuildVersion() {
        return getProperty(BUILD_ID_KEY, "0");
    }

    /**
     * Returns version prefix
     */
    public static String getVersionPrefix() {
        return getProperty(VERSION_PREFIX_KEY, "");
    }

    /**
     * Returns version suffix
     */
    public static String getVersionSuffix() {
        return getProperty(VERSION_SUFFIX_KEY, "");
    }

    /**
     * Returns Proper Product Name
     */
    public static String getProductName() {
        return getProperty(PRODUCT_NAME_KEY,
                "Undefined Product Name - define product and version info in config/branding");
    }

    /**
     * Returns Abbreviated Product Name
     */
    public static String getAbbrevProductName() {
        return getProperty(ABBREV_PRODUCT_NAME_KEY, "undefined");
    }

    /**
     * Returns template name use to create default domain.
     */
    public static String getDefaultDomainTemplate() {
        return getProperty(DEFAULT_DOMAIN_TEMPLATE_NAME, DEFAULT_DOMAIN_TEMPLATE_JAR);
    }

    /**
     * Returns the admin client command string which represents the name of the
     * command use for performing admin related domain tasks.
     */
    public static String getAdminClientCommandName() {
        return getProperty(ADMIN_CLIENT_COMMAND_NAME_KEY, "nadmin");
    }

    public static String getInitialAdminGroups() {
        return getProperty(INITIAL_ADMIN_GROUPS_KEY, "asadmin");
    }

    /*
     * Fetch the value for the property identified by key
     * from the first Properties object in the list. If it doesn't exist
     * look in the based on Properties, recursively. If still not found,
     * return the default, def.
     */
    private static String getProperty(String key, String def) {
        return getProperty(versionProp, key, def);
    }

    private static String getProperty(Properties p, String key, String def) {
        if (p == null) {
            return def;
        }
        String v = p.getProperty(key);
        if (v != null) {
            return v;
        }
        String basedon = p.getProperty(BASED_ON_KEY);
        if (basedon != null) {
            Properties bp = versionPropsMap.get(basedon);
            if (bp != null) {
                return getProperty(bp, key, def);
            }
        }
        return def;
    }
}
