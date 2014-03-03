package views.helpers

import org.pegdown.{LinkRenderer, ToHtmlSerializer}
import org.pegdown.ast.{VerbatimNode, InlineHtmlNode, HtmlBlockNode}
import org.apache.commons.lang3.StringUtils

/**
 * <p>Extends PegDown serializer to archive the following:
 *
 * <p>1. Prevents XSS attack by escaping any tags in article/comment contents,
 * which are not parsed as explicit code by markdown parser
 * <p>2. Adds source code formatting CSS classes
 */
class EscapingToHtmlSerializer extends ToHtmlSerializer(new LinkRenderer){

  override def visit(node : HtmlBlockNode) {
    val text = node.getText
    if (text.length() > 0) printer.println()
    printer.printEncoded(text)
  }

  override def visit(node : InlineHtmlNode ) = printer.printEncoded(node.getText)

  override def visit(node: VerbatimNode) = {
    printer.println().print("<pre class='prettyprint linenums'><code")
    if (!StringUtils.isEmpty(node.getType)) {
      printer.print(s" class='lang-${node.getType}'")
    }
    printer.print(">")
    printer.printEncoded(node.getText)
    printer.print("</code></pre>")
  }
}