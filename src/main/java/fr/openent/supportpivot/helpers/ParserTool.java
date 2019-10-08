package fr.openent.supportpivot.helpers;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class ParserTool {

    private static final Logger log = LoggerFactory.getLogger(ParserTool.class);

    public static Document getParsedXml(Buffer buffer) {
        return ParserTool.buildXml(new String(buffer.getBytes()));
    }

    public static Document getParsedXml(String stringXml) {
        return ParserTool.buildXml(stringXml);
    }

    public static String getStringFromDocument(Document xml) {
        try {
            DOMSource domSource = new DOMSource(xml);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.transform(domSource, result);
            return writer.toString();
        } catch (TransformerException e) {
            log.info(e.getMessage(), (Object) e.getStackTrace());
            return null;
        }
    }

    private static Document buildXml(String stringToRead) {
        Document xml = null;
        try {
            DocumentBuilder builder = ParserTool.initBuilder();
            InputSource source = new InputSource(new StringReader(stringToRead));

            xml = builder.parse(source);
            xml.getDocumentElement().normalize();
        } catch (SAXException | IOException e) {
            log.info(e.getMessage(), (Object) e.getStackTrace());
        }
        return xml;
    }

    private static DocumentBuilder initBuilder() {
        DocumentBuilder builder = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            log.info(e.getMessage(), (Object) e.getStackTrace());
        }
        return builder;
    }
}
