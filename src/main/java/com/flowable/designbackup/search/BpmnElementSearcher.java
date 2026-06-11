package com.flowable.designbackup.search;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Element-level search inside BPMN and CMMN XML: every identifiable element
 * (any element carrying an {@code id} attribute below the root containers) is
 * evaluated individually against the condition, so e.g. NOT_CONTAINS returns
 * each element of the selected type that does not mention the term.
 */
@Component
public class BpmnElementSearcher {

    private static final List<String> FLOWABLE_NAMESPACES =
            List.of("http://flowable.org/bpmn", "http://flowable.org/cmmn");
    private static final Set<String> CONTAINER_ELEMENTS =
            Set.of("definitions", "process", "case", "casePlanModel");

    public List<ElementMatch> findMatchingElements(String bpmnXml, SearchCondition condition, String term,
            String elementTypeFilter) {
        Document document = parse(bpmnXml);
        List<ElementMatch> matches = new ArrayList<>();
        NodeList allElements = document.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            if (!isSearchableElement(element) || !matchesElementType(element, elementTypeFilter)) {
                continue;
            }
            if (condition.matches(serialize(element), term)) {
                matches.add(toElementMatch(element));
            }
        }
        return List.copyOf(matches);
    }

    private boolean isSearchableElement(Element element) {
        return element.hasAttribute("id") && !CONTAINER_ELEMENTS.contains(element.getLocalName());
    }

    private boolean matchesElementType(Element element, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return filter.equalsIgnoreCase(element.getLocalName())
                || filter.equalsIgnoreCase(flowableAttribute(element, "type"))
                || filter.equalsIgnoreCase(flowableAttribute(element, "extensionId"));
    }

    private ElementMatch toElementMatch(Element element) {
        return new ElementMatch(
                element.getAttribute("id"),
                element.hasAttribute("name") ? element.getAttribute("name") : null,
                element.getLocalName(),
                flowableAttribute(element, "type"));
    }

    private String flowableAttribute(Element element, String name) {
        for (String namespace : FLOWABLE_NAMESPACES) {
            String namespaced = element.getAttributeNS(namespace, name);
            if (namespaced != null && !namespaced.isEmpty()) {
                return namespaced;
            }
        }
        String prefixed = element.getAttribute("flowable:" + name);
        return prefixed.isEmpty() ? null : prefixed;
    }

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse model XML: " + e.getMessage(), e);
        }
    }

    private String serialize(Node node) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize BPMN element", e);
        }
    }
}
