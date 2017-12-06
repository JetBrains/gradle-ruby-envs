package com.jetbrains.ruby.envs

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Delete

class RubyEnvsPlugin implements Plugin<Project> {
    private static Boolean isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
    private static Boolean isUnix = Os.isFamily(Os.FAMILY_UNIX)

    private static rvmFolderName = "rvm"
    private static rubyBuildFolderName = "ruby-build"

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

    private static Task createRubyUnixTask(Project project, Ruby ruby) {
        return project.tasks.create(name: "Bootstrap Ruby $ruby.version via $ruby.tool") {
            switch (ruby.tool) {
                case "rvm":
                    dependsOn "install_rvm"

                    doLast {
                        String command = "source '${getRvmExecutable(project)}' && " +
                                "rvm install $ruby.version --autolibs=read-only"
                        project.logger.quiet("Executing '$command'")
                        project.exec {
                            commandLine "bash", "-c", command
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

    @Override
    void apply(Project project) {
        project.mkdir(project.buildDir)
        RubyEnvsExtension envs = project.extensions.create("envs", RubyEnvsExtension.class)

        project.afterEvaluate {
            createInstallRvmTask(project, new File(envs.bootstrapDirectory, rvmFolderName))
            createInstallRubyBuildTask(project, new File(envs.bootstrapDirectory, rubyBuildFolderName))

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
                        // TODO
                    }
                }
            }
        }
    }

}
