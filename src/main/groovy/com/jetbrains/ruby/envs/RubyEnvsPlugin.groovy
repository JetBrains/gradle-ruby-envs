package com.jetbrains.ruby.envs

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Delete

class RubyEnvsPlugin implements Plugin<Project> {
    private static Boolean isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
    private static Boolean isUnix = Os.isFamily(Os.FAMILY_UNIX)

    private static Task createInstallRvmTask(Project project, File installDir) {
        return project.tasks.create(name: 'install_rvm') {
            onlyIf {
                !installDir.exists() && isUnix
            }

            doLast {
                project.logger.quiet("Downloading & Installing rvm")
//                project.ant.mkdir(dir: installDir)
                String command = "curl -sSL https://get.rvm.io | bash -s -- --path $installDir"
                project.logger.quiet("Executing '$command'")

                project.exec {
                    commandLine "bash", "-c", command
                }
            }
        }
    }

    private static File getRvmExecutable(Project project) {
        return new File(project.buildDir, "rvm/scripts/rvm")
    }

    private static Task createRubyTask(Project project, Ruby ruby) {
        return project.tasks.create(name: "Bootstrap Ruby $ruby.version") {
            dependsOn "install_rvm"

            doLast {
                String command = "source '${getRvmExecutable(project)}' && rvm install $ruby.version  --autolibs=read-only"
                project.logger.quiet("Executing '$command'")
                project.exec {
                    commandLine "bash", "-c", command
                }
            }
        }
    }

    @Override
    void apply(Project project) {
        RubyEnvsExtension envs = project.extensions.create("envs", RubyEnvsExtension.class)

        project.afterEvaluate {

            createInstallRvmTask(project, envs.rvmDirectory)

            project.tasks.create(name: "clean_rvm_directory", type: Delete) {
                delete envs.rvmDirectory
            }

            project.tasks.create(name: 'build_rubies') {
                onlyIf { !envs.rubies.empty }

                envs.rubies.each { Ruby ruby ->
                    dependsOn createRubyTask(project, ruby)
                }
            }
        }
    }
}
