package com.jetbrains.ruby.envs

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.util.VersionNumber


class RubyEnvsPlugin implements Plugin<Project> {
    private static Boolean isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
    private static Boolean isUnix = Os.isFamily(Os.FAMILY_UNIX)

    private static rvmFolderName = "rvm"
    private static rubyBuildFolderName = "ruby-build"
    private static rubyDevKitFolderName = "devkit"

    private static Task createInstallRvmTask(Project project, File installDir) {
        return project.tasks.create(name: 'install_rvm') {
            onlyIf {
                !installDir.exists() && isUnix
            }

            doLast {
                project.logger.quiet("Downloading & Installing rvm")
                String command = "curl -sSL https://get.rvm.io | bash -s -- --path $installDir"
                project.logger.quiet("Executing '$command'")

                project.exec {
                    commandLine "bash", "-c", command
                }
            }
        }
    }

    private static File getRvmExecutable(Project project) {
        return new File(project.extensions.findByName("envs").getProperty("bootstrapDirectory") as File,
                "$rvmFolderName/bin/rvm")
    }

    private static File getRvmScriptExecutable(Project project) {
        return new File(project.extensions.findByName("envs").getProperty("bootstrapDirectory") as File,
                "$rvmFolderName/scripts/rvm")
    }

    private static Task createInstallRubyBuildTask(Project project, File installDir) {
        return project.tasks.create(name: 'install_ruby_build') {
            onlyIf {
                !installDir.exists() && isUnix
            }

            doLast {
                File rubyBuild = new File(project.buildDir, "ruby-build.zip")
                project.logger.quiet("Downloading latest ruby-build from github")
                project.ant.get(dest: rubyBuild) {
                    url(url: "https://github.com/rbenv/ruby-build/archive/master.zip")
                }
                project.ant.unzip(src: rubyBuild, dest: project.buildDir)
                new File(project.buildDir, "ruby-build-master").with { src ->
                    project.ant.move(file: src, tofile: installDir)
                }
                rubyBuild.delete()
            }
        }
    }

    private static File getRubyBuildExecutable(Project project) {
        return new File(project.extensions.findByName("envs").getProperty("bootstrapDirectory") as File,
                "$rubyBuildFolderName/bin/ruby-build")
    }

    private static Task createInstallDevKitTask(Project project, File installDir) {
        return project.tasks.create(name: 'install_devkit') {
            onlyIf {
                !installDir.exists() && isWindows
            }

            doLast {
                File devKit = new File(project.buildDir, "devkit.exe")
                project.logger.quiet("Downloading DevKit")
                project.ant.get(dest: devKit) {
                    if (ruby.is64) {
                        url(url: "https://dl.bintray.com/oneclick/rubyinstaller/DevKit-mingw64-64-4.7.2-20130224-1432-sfx.exe")
                    } else {
                        url(url: "https://dl.bintray.com/oneclick/rubyinstaller/DevKit-mingw64-32-4.7.2-20130224-1151-sfx.exe")
                    }
                }

                project.logger.quiet("Installing DevKit to $installDir")
                executePlainCommand(project, "$devKit -o\"$installDir\" -y")

                devKit.delete()
            }
        }
    }

    private static Task createRubyUnixTask(Project project, Ruby ruby) {
        return project.tasks.create(name: "Bootstrap Ruby $ruby.version via $ruby.tool") {
            switch (ruby.tool) {
                case "rvm":
                    dependsOn "install_rvm"

                    doLast {
                        String command = "source '${getRvmScriptExecutable(project)}' && " +
                                "${getRvmExecutable(project)} install $ruby.version --autolibs=read-only"
                        project.logger.quiet("Executing '$command'")
                        project.exec {
                            commandLine "bash", "-c", command
                            environment PATH: "/bin:/usr/bin", GEM_HOME: "", GEM_PATH: "", rvm_path: ""
                        }
                    }

                    break
                case "rbenv":
                    dependsOn "install_ruby_build"

                    doLast {
                        File rubyBuildExecutable = getRubyBuildExecutable(project)
                        String command = "chmod +x $rubyBuildExecutable && " +
                                "$rubyBuildExecutable $ruby.version $ruby.dir"
                        project.logger.quiet("Executing '$command'")
                        project.exec {
                            commandLine "bash", "-c", command
                        }
                    }

                    break
            }
        }
    }

    private static Task createRubyWindowsTask(Project project, Ruby ruby) {
        return project.tasks.create(name: "Bootstrap Ruby $ruby.version") {
            if (VersionNumber.parse(ruby.version) < VersionNumber.parse("2.4")) {
                dependsOn "install_devkit"
            }

            onlyIf {
                !ruby.dir.exists() && isWindows
            }

            doLast {
                String urlString
                if (VersionNumber.parse(ruby.version) >= VersionNumber.parse("2.4")) {
                    String architecture = { if (ruby.is64) "x64" else "x86" }.call()
                    urlString = "https://github.com/oneclick/rubyinstaller2/releases/download/rubyinstaller-$ruby.version/rubyinstaller-$ruby.version-${architecture}.exe"
                } else {
                    String architecture = { if (ruby.is64) "x64" else "i386" }.call()
                    urlString = "https://dl.bintray.com/oneclick/rubyinstaller/rubyinstaller-$ruby.version-${architecture}.exe"
                }

                String installerName = urlString.substring(urlString.lastIndexOf('/') + 1, urlString.length())
                File installer = new File(project.buildDir, installerName)
                project.logger.quiet("Downloading $installerName installer from $urlString")
                project.ant.get(dest: installer) {
                    url(url: urlString)
                }

                executePlainCommand(project, "$installer /verysilent /dir=\"$ruby.dir\"")

                installer.delete()

                if (VersionNumber.parse(ruby.version) < VersionNumber.parse("2.4")) {
                    File devKitFolder = new File(project.extensions.findByName("envs").getProperty("bootstrapDirectory") as File,
                            rubyDevKitFolderName)
                    executePlainCommand(project, "$ruby.dir\\bin\\ruby.exe dk.rb init --force", devKitFolder)
                    executePlainCommand(project, "$ruby.dir\\bin\\ruby.exe dk.rb install", devKitFolder)
                }
            }
        }
    }

    @Override
    void apply(Project project) {
        project.mkdir(project.buildDir)
        RubyEnvsExtension envs = project.extensions.create("envs", RubyEnvsExtension.class)

        project.afterEvaluate {
            createInstallRvmTask(project, new File(envs.bootstrapDirectory, rvmFolderName))
            createInstallRubyBuildTask(project, new File(envs.bootstrapDirectory, rubyBuildFolderName))
            createInstallDevKitTask(project, new File(envs.bootstrapDirectory, rubyDevKitFolderName))

            project.tasks.create(name: "clean_directories", type: Delete) {
                delete envs.bootstrapDirectory
                delete envs.envsDirectory
            }

            project.tasks.create(name: 'build_rubies') {
                onlyIf { !envs.rubies.empty }

                envs.rubies.each { Ruby ruby ->
                    if (isUnix) {
                        dependsOn createRubyUnixTask(project, ruby)
                    } else if (isWindows) {
                        dependsOn createRubyWindowsTask(project, ruby)
                    }
                }
            }
        }
    }

    private static void executePlainCommand(Project project, String command, File workDir = null) {
        project.logger.quiet("Executing '$command'")
        project.exec {
            commandLine command.split(" ")
            if (workDir != null) workingDir workDir
        }
    }
}
