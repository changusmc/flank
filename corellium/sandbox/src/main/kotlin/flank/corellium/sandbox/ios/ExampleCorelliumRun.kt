@file:JvmName("ExampleCorelliumRun")
package flank.corellium.sandbox.ios

import flank.corellium.client.agent.disconnect
import flank.corellium.client.agent.uploadFile
import flank.corellium.client.core.connectAgent
import flank.corellium.client.core.connectCorellium
import flank.corellium.client.core.createNewInstance
import flank.corellium.client.core.getAllProjects
import flank.corellium.client.core.getInstanceInfo
import flank.corellium.client.core.getProjectInstancesList
import flank.corellium.client.core.waitUntilInstanceIsReady
import flank.corellium.client.data.Instance
import flank.corellium.client.data.Instance.BootOptions
import flank.corellium.sandbox.config.Config
import kotlinx.coroutines.runBlocking
import java.io.File

private val instanceName = Config.instanceName
private val pathStringToUploadTo = Config.pathToUploadPlist
private val localPathString = Config.plistPath
private val xctestrunPath = Config.xctestrunPath

fun main() = runBlocking {
    val client = connectCorellium(
        api = Config.api,
        username = Config.username,
        password = Config.password
    )

    println("Fetching [Amanda] project")
    val projectId = client.getAllProjects().first { it.name == "Amanda" }.id

    println("Looking for $instanceName instance")
    val instanceId = client.getProjectInstancesList(projectId)
        .find { it.name == instanceName }?.id
        ?: run {
            println("Instance not found. Creating new one")
            client.createNewInstance(
                Instance(
                    project = projectId,
                    name = instanceName,
                    flavor = Config.flavor,
                    os = Config.os,
                    bootOptions = BootOptions(
                        udid = Config.udid
                    ),
                    patches = listOf("corelliumd")
                )
            )
        }

    println("Wait until instance is ready (may take some time ~3-5 min)")
    client.waitUntilInstanceIsReady(instanceId)

    val instance = client.getInstanceInfo(instanceId)

    println("Creating agent")
    println("Await agent is connected and ready to use")
    val agent = client.connectAgent(instance.agent?.info ?: error("Agent info is not present"))
    println("Agent ready")

    println("Uploading plist file")
    agent.uploadFile(
        path = pathStringToUploadTo,
        bytes = File(localPathString).readBytes()
    )

    ProcessBuilder(
        "xcodebuild",
        "test-without-building",
        "-allowProvisioningUpdates",
        "-allowProvisioningDeviceRegistration",
        "-xctestrun",
        xctestrunPath,
        "-destination",
        "id=${instance.bootOptions.udid}"
    )
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start().waitFor()

    agent.disconnect()
}
