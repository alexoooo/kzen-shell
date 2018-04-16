package tech.kzen.shell.resource

import com.samskivert.mustache.Mustache
import org.omg.CORBA.Object
import tech.kzen.shell.model.ProjectModel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths


class ResourceTemplate {
    //-----------------------------------------------------------------------------------------------------------------
    fun render(
            templates: ResourceTree,
            model: ProjectModel
    ): ResourceTree {
        val files = mutableMapOf<Path, ByteArray>()

        for (file in templates.files) {
            val path = renderPath(file.key, model)
            val body = renderBody(file.value, model)

            files[path] = body
        }

        return ResourceTree(files)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun renderPath(
            templatePath: Path,
            model: ProjectModel
    ): Path {
        val asString = templatePath.toString()

        val rendered = render(asString, model)

        val parsed = Paths.get(rendered)

        val expandedParent = parsed
                .parent.toString().replace(".", "/")

        return Paths.get(expandedParent, parsed.fileName.toString())
    }


    private fun renderBody(
            bodyTemplate: ByteArray,
            model: ProjectModel
    ): ByteArray {
        val asString = bodyTemplate.toString(StandardCharsets.UTF_8)

        val rendered = render(asString, model)

        return rendered.toByteArray(StandardCharsets.UTF_8)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun render(
            template: String,
            model: ProjectModel
    ): String {
        if (! template.contains("{{")) {
            return template
        }

        val mustacheTemplate = Mustache.compiler().compile(template)

        return mustacheTemplate.execute(model)
    }
}