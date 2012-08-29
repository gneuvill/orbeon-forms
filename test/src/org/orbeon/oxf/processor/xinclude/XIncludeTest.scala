/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.processor.xinclude

import javax.xml.namespace.QName
import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.xml.XMLUtils.ParserConfiguration.XINCLUDE_ONLY
import org.orbeon.oxf.xml.{XMLReceiverAdapter, XMLUtils}
import org.orbeon.scaxon.SAXEvents._
import org.scalatest.junit.AssertionsForJUnit
import org.xml.sax.Attributes

class XIncludeTest extends ResourceManagerTestBase with AssertionsForJUnit {

    implicit def tupleToQName(tuple: (String, String)) = new QName(tuple._1, tuple._2, "")
    
    val XML    = "http://www.w3.org/XML/1998/namespace"
    val XI     = "http://www.w3.org/2001/XInclude"
    val HTML   = "http://www.w3.org/1999/xhtml"
    val XForms = "http://www.w3.org/2002/xforms"
    val SVG    = "http://www.w3.org/2000/svg"

    val XMLBase: QName = XML → "base"

    @Test def basicInclude() {

        val collector = new Collector
        
        XMLUtils.urlToSAX("oxf:/org/orbeon/oxf/processor/xinclude/include11.xml", collector, XINCLUDE_ONLY, true)
        
        val expected = Seq(
            StartDocument,
                StartPrefixMapping("", HTML),
                StartPrefixMapping("xforms", XForms),
                StartPrefixMapping("xi", XI),
                    StartElement(HTML → "html", Atts(Seq())),
                        StartPrefixMapping("xforms", ""),
                        StartPrefixMapping("xi", ""),
                        StartPrefixMapping("svg", SVG),
                            StartElement(HTML → "body", Atts(Seq((XMLBase, "oxf:/org/orbeon/oxf/processor/xinclude/include12.xml")))),
                                StartElement(HTML → "div", Atts(Seq())),
                                EndElement(HTML → "div"),
                            EndElement(HTML → "body"),
                        EndPrefixMapping("xforms"),
                        EndPrefixMapping("xi"),
                        EndPrefixMapping("svg"),
                    EndElement(HTML → "html"),
                EndPrefixMapping(""),
                EndPrefixMapping("xforms"),
                EndPrefixMapping("xi"),
            EndDocument
        )

        assert(expected === collector.events)
    }

    @Test def includeAtRoot() {

        val collector = new Collector

        XMLUtils.urlToSAX("oxf:/org/orbeon/oxf/processor/xinclude/include21.xml", collector, XINCLUDE_ONLY, true)
        
        val expected = Seq(
            StartDocument,
                StartPrefixMapping("", HTML),
                StartPrefixMapping("xforms", XForms),
                StartPrefixMapping("xi", XI),
                    StartElement(HTML → "html", Atts(Seq())),
                        StartPrefixMapping("xforms", ""),
                        StartPrefixMapping("xi", ""),
                            StartElement(HTML → "body", Atts(Seq((XMLBase, "oxf:/org/orbeon/oxf/processor/xinclude/include23.xml")))),
                                StartElement(HTML → "div", Atts(Seq())),
                                EndElement(HTML → "div"),
                            EndElement(HTML → "body"),
                        EndPrefixMapping("xforms"),
                        EndPrefixMapping("xi"),
                    EndElement(HTML → "html"),
                EndPrefixMapping(""),
                EndPrefixMapping("xforms"),
                EndPrefixMapping("xi"),
            EndDocument
        )

        assert(expected === collector.events)
    }

    @Test def noDuplicateNSEvents() {

        val collector = new Collector

        XMLUtils.urlToSAX("oxf:/org/orbeon/oxf/processor/xinclude/include31.xml", collector, XINCLUDE_ONLY, true)

        val expected = Seq(
            StartDocument,
                StartPrefixMapping("", HTML),
                StartPrefixMapping("xforms", XForms),
                    StartElement(HTML → "html", Atts(Seq())),
                        StartElement(HTML → "body", Atts(Seq((XMLBase, "oxf:/org/orbeon/oxf/processor/xinclude/include32.xml")))),
                        EndElement(HTML → "body"),
                    EndElement(HTML → "html"),
                EndPrefixMapping(""),
                EndPrefixMapping("xforms"),
            EndDocument
        )

        assert(expected === collector.events)
    }

    @Test def siblingIncludes() {

        val collector = new Collector

        XMLUtils.urlToSAX("oxf:/org/orbeon/oxf/processor/xinclude/include41.xml", collector, XINCLUDE_ONLY, true)

        val expected = Seq(
            StartDocument,
                StartPrefixMapping("", HTML),
                StartPrefixMapping("xforms", XForms),
                    StartElement(HTML → "html", Atts(Seq())),
                        StartPrefixMapping("", ""),
                        StartPrefixMapping("xforms", ""),
                        StartPrefixMapping("xhtml", HTML),
                            StartElement(HTML → "head", Atts(Seq((XMLBase, "oxf:/org/orbeon/oxf/processor/xinclude/include42.xml")))),
                            EndElement(HTML → "head"),
                        EndPrefixMapping(""),
                        EndPrefixMapping("xforms"),
                        EndPrefixMapping("xhtml"),
                        StartPrefixMapping("xforms", ""),
                            StartElement(HTML → "body", Atts(Seq((XMLBase, "oxf:/org/orbeon/oxf/processor/xinclude/include43.xml")))),
                            EndElement(HTML → "body"),
                        EndPrefixMapping("xforms"),
                    EndElement(HTML → "html"),
                EndPrefixMapping(""),
                EndPrefixMapping("xforms"),
            EndDocument
        )

        assert(expected === collector.events)
    }

    // Collect the SAX events we are interested in
    class Collector extends XMLReceiverAdapter {

        private var _events: List[SAXEvent] = Nil
        def events = _events.reverse

        override def startDocument(): Unit = _events ::= StartDocument
        override def endDocument(): Unit   = _events ::= EndDocument

        override def startPrefixMapping(prefix: String, uri: String): Unit =
            _events ::= StartPrefixMapping(prefix, uri)

        override def endPrefixMapping(prefix: String): Unit =
            _events ::= EndPrefixMapping(prefix)

        override def startElement(namespaceURI: String, localName: String, qName: String, atts: Attributes): Unit =
            _events ::= StartElement(namespaceURI, localName, qName, atts)

        override def endElement(namespaceURI: String, localName: String, qName: String): Unit =
            _events ::= EndElement(namespaceURI, localName, qName)
    }
}
