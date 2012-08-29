/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xml.NamespaceMapping
import java.util.{Map ⇒ JMap}
import org.orbeon.saxon.om.{Item, ValueRepresentation}
import collection.JavaConverters._

trait ControlXPathSupport {

    self: XFormsControl ⇒

    private def getNamespaceMappings =
        if (staticControl ne null) staticControl.namespaceMapping else container.getNamespaceMappings(element)

    /**
     * Evaluate an attribute of the control as an AVT.
     *
     * @param attributeValue    value of the attribute
     * @return                  value of the AVT or null if cannot be computed
     */
    def evaluateAvt(attributeValue: String) = {
        if (! XFormsUtils.maybeAVT(attributeValue))
            // Definitely not an AVT
            attributeValue
        else {
            // Possible AVT

            // NOTE: the control may or may not be bound, so don't use getBoundItem()
            val contextNodeset = bindingContext.getNodeset
            if (contextNodeset.size == 0)
                null // TODO: in the future we should be able to try evaluating anyway
            else {
                // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
                // Reason is that XPath functions might use the context stack to get the current model, etc.
                getContextStack.setBinding(getBindingContext)
                // Evaluate
                try
                    XPathCache.evaluateAsAvt(contextNodeset, bindingContext.getPosition, attributeValue, getNamespaceMappings,
                        bindingContext.getInScopeVariables, XFormsContainingDocument.getFunctionLibrary, getFunctionContext, null, getLocationData)
                catch {
                    case e: Exception ⇒
                        // Don't consider this as fatal
                        XFormsError.handleNonFatalXPathError(container, e)
                        null
                } finally
                    // Restore function context to prevent leaks caused by context pointing to removed controls
                    returnFunctionContext()
            }
        }
    }

    // Evaluate an XPath expression as a string in the context of this control.
    def evaluateAsString(xpathString: String, contextItems: Seq[Item], contextPosition: Int): Option[String] = {
        // NOTE: the control may or may not be bound, so don't use getBoundNode()
        if (contextItems.isEmpty)
            None
        else {
            // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
            // Reason is that XPath functions might use the context stack to get the current model, etc.
            getContextStack.setBinding(getBindingContext)
            try
                Option(XPathCache.evaluateAsString(contextItems.asJava, contextPosition, xpathString, getNamespaceMappings,
                    bindingContext.getInScopeVariables, XFormsContainingDocument.getFunctionLibrary, getFunctionContext, null, getLocationData))
            catch {
                case e: Exception ⇒
                    // Don't consider this as fatal
                    XFormsError.handleNonFatalXPathError(container, e)
                    None
            } finally
                // Restore function context to prevent leaks caused by context pointing to removed controls
                returnFunctionContext()
        }
    }

    // Evaluate an XPath expression as a string in the context of this control.
    def evaluateAsString(xpathString: String, contextItem: Option[Item], namespaceMapping: NamespaceMapping, variableToValueMap: JMap[String, ValueRepresentation]): Option[String] =
        contextItem match {
            case None ⇒ None
            case Some(contextItem) ⇒
                // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
                // Reason is that XPath functions might use the context stack to get the current model, etc.
                getContextStack.setBinding(getBindingContext)
                try
                    Option(XPathCache.evaluateAsString(contextItem, xpathString, namespaceMapping, variableToValueMap,
                        XFormsContainingDocument.getFunctionLibrary, getFunctionContext, null, getLocationData))
                catch {
                    case e: Exception ⇒
                        // Don't consider this as fatal
                        XFormsError.handleNonFatalXPathError(container, e)
                        None
                } finally
                    // Restore function context to prevent leaks caused by context pointing to removed controls
                    returnFunctionContext()
        }

    // Return an XPath function context having this control as source control.
    private def getFunctionContext =
        getContextStack.getFunctionContext(getEffectiveId)

    private def returnFunctionContext() =
        getContextStack.returnFunctionContext()
}
