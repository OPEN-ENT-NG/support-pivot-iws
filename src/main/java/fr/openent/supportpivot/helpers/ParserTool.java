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
import java.io.IOException;
import java.io.StringReader;

public class ParserTool {

    private static final Logger log = LoggerFactory.getLogger(ParserTool.class);

    public static Document getParsedXml(Buffer buffer) {
        Document xml = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource source = new InputSource(new StringReader(new String(buffer.getBytes())));

            xml = builder.parse(source);
            xml.getDocumentElement().normalize();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.info(e.getMessage(), (Object) e.getStackTrace());
            // Todo: Error
        }
        return xml;
    }
}
