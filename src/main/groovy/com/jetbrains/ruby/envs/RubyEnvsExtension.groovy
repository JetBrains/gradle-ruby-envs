package com.jetbrains.ruby.envs

/**
 * Project extension to configure Ruby build environment.
 *
 */
class RubyEnvsExtension {
    File bootstrapDirectory
    File envsDirectory

    List<Ruby> rubies = []

    void ruby(final String version, final String tool) {
        assert tool in ["rvm", "rbenv"]
        rubies << new Ruby(version, tool, envsDirectory)
    }
}


class Ruby {
    final String version
    final String tool
    final File dir

    Ruby(String version, String tool, File envsDir = null) {
        this.version = version
        this.tool = tool
        this.dir = new File(envsDir, version)
    }
}