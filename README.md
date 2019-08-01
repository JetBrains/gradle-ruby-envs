Gradle Ruby Envs Plugin [![JetBrains team project](https://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
========================


Usage
-----
                                                
Apply the plugin to your project following
https://plugins.gradle.org/plugin/com.jetbrains.ruby.envs,
and configure the associated extension:

```gradle
envs {
    bootstrapDirectory = new File(buildDir, 'bootstrap')
    envsDirectory = new File(buildDir, 'envs')

    if (Os.isFamily(Os.FAMILY_UNIX)) {
        ruby "2.3.1", "rvm"
        ruby "2.3.1", "rbenv"
    }

    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        ruby "2.3.1"
        ruby "2.3.1"
    }
}
```

Then invoke the `build_rubies` task. 

This will download and install specified Ruby envs to `buildDir/envs`.
