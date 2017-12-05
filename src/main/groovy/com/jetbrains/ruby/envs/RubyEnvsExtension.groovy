package com.jetbrains.ruby.envs

/**
 * Project extension to configure Ruby build environment.
 *
 */
class RubyEnvsExtension {
    File rvmDirectory

    List<Ruby> rubies = []

    void ruby(final String version) {
        rubies << new Ruby(version)
    }
}


class Ruby {
    final String version

    Ruby(String version = null) {
        this.version = version
    }
}