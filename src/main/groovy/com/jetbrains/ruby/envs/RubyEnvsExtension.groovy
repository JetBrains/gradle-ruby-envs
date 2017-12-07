package com.jetbrains.ruby.envs

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.util.VersionNumber

/**
 * Project extension to configure Ruby build environment.
 *
 */
class RubyEnvsExtension {
    File bootstrapDirectory
    File envsDirectory

    Boolean _64Bits = true  // By default 64 bit envs should be installed

    List<Ruby> rubies = []

    void ruby(final String version, final String architecture = null, final String tool = null) {
        assert VersionNumber.parse(version) >= VersionNumber.parse("2.0") :
                "Versions lower 2.0 aren't supported"
        if (tool != null && Os.isFamily(Os.FAMILY_UNIX)) assert tool in ["rvm", "rbenv"] :
                "Only rvm and rbenv (ruby-build) for Unix are supported"
        rubies << new Ruby(version, envsDirectory, is64(architecture), tool)
    }

    private Boolean is64(final String architecture) {
        return (architecture == null) ? _64Bits : !(architecture == "32")
    }
}


class Ruby {
    final String version
    final File dir
    final Boolean is64
    final String tool

    Ruby(String version, File envsDir, Boolean is64 = true, String tool = null) {
        this.version = version
        this.dir = new File(envsDir, version)
        this.is64 = is64
        this.tool = tool
    }
}