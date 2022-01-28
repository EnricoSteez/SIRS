//import com.att.research.xacml.api.pdp.PDPEngine;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.balana.Balana;
import org.wso2.balana.PDP;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PDPServer {
    private static final Logger logger = Logger.getLogger(PDPServer.class.getName());

    private final int port;
    private Server server;
    private static Balana balana;
    private static PDP pdp;
    //private static PDPEngine pdpEngine;
    //private static PdpEngineInoutAdapter<Request, Response> authzforcePdp;


    public PDPServer (int port, String operationMode) {
        this.port = port;

//        BALANA
        initBalana(operationMode);
        pdp = getPDPNewInstance();

////        AT&T (THROWS ERROR)
//        PDPEngineFactory factory = null;
//        try {
//            factory = PDPEngineFactory.newInstance();
//            pdpEngine = factory.newEngine();
//        } catch (FactoryException e) {
//            e.printStackTrace();
//            System.exit(0);
//        }

//        AUTHZFORCE (CAN'T LOCATE THE CONFIGURATION)
//        try {
//            PdpEngineConfiguration conf = PdpEngineConfiguration.getInstance("classpath:resources/pdpconfig.xml");
//            authzforcePdp = PdpEngineAdapters.newXacmlJaxbInoutAdapter(conf);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    private static void initBalana (String operationMode) {
        try {
            // using file based policy repository. so set the policy location as system property
            String policyLocation;
            if(operationMode.equals("NormalMode"))
                policyLocation = (new File(".")).getCanonicalPath() + File.separator + "resources";
            else
                policyLocation = (new File(".")).getCanonicalPath() + File.separator + "AlternativePolicies";

            System.setProperty(FileBasedPolicyFinderModule.POLICY_DIR_PROPERTY, policyLocation);
        } catch (IOException e) {
            System.err.println("Can not locate policy repository");
        }
        // create default instance of Balana
        balana = Balana.getInstance();
    }

    /**
     * Returns a new PDP instance with new XACML policies
     *
     * @return a  PDP instance
     */
    private static PDP getPDPNewInstance(){
//        PDPConfig pdpConfig = balana.getPdpConfig();
//        // registering new attribute finder. so default PDPConfig is needed to change
//        AttributeFinder attributeFinder = pdpConfig.getAttributeFinder();
//        List<AttributeFinderModule> finderModules = attributeFinder.getModules();
//        finderModules.add(new MedicalRecordsAttributeFinderModule());
//        attributeFinder.setModules(finderModules);
//        return new PDP(new PDPConfig(attributeFinder, pdpConfig.getPolicyFinder(), null, true));
//
//        I THINK THAT DEFAULT CONFIG IS FINE IN THIS CASE (???)
        return new PDP(balana.getPdpConfig());
    }

    /** Start serving requests. */
    private void start () throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new AccessControlService())
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        logger.setLevel(Level.INFO);
    }

    /** Stop serving requests and shutdown resources. */
    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main method.  This comment makes the linter happy.
     */
    public static void main(String[] args) throws Exception {
        if(args.length > 1) {
            System.out.println("Usage: PDPServer [PandemicMode]");
            System.out.println("Specify PandemicMode to start the PDP in, guess what mode...");
            System.out.println("Without arguments, the PDP will use a Normal Mode policy");
            System.exit(0);
        }
        String operationMode = "NormalMode";
        if(args.length == 1 && args[0].equals("PandemicMode")) {
            operationMode = "PandemicMode";
        }

        final PDPServer server = new PDPServer(8980,operationMode);

        server.start();
        server.blockUntilShutdown();
    }

    static class AccessControlService extends AccessControlServiceGrpc.AccessControlServiceImplBase {
        @Override
        public void dummyValidationForTesting (DummyValidationRequest request, StreamObserver<DummyValidationReply> responseObserver) {
//            super.dummyValidationForTesting(request, responseObserver);
            DummyValidationReply reply = DummyValidationReply.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void validateAccess (AccessControlRequest request, StreamObserver<AccessControlReply> responseObserver) {
//            super.validateAccess(request, responseObserver);
            System.out.println("RECEIVED REQUEST: ");
            System.out.println(request.getXacmlRequest());
            System.out.println(toPrettyString(request.getXacmlRequest()));

////           ****************************** BALANA ******************************
            String balanaEvaluation = pdp.evaluate(request.getXacmlRequest());
            System.out.println("EVALUATED REQUEST.\nBALANA OUTCOME: ");
            System.out.println(toPrettyString(balanaEvaluation));

////           ****************************** AT&T ******************************
//            Response attResponse = null;
//            Request pepRequest = null;
//            try {
//                pepRequest = DOMRequest.load(request.getXacmlRequest());
//                attResponse = pdpEngine.decide(pepRequest);
//            } catch (DOMStructureException|PDPException e) {
//                e.printStackTrace();
//            }
//            assert attResponse != null;

//            System.out.println("AT&T OUTCOME: ");
//            System.out.println(attResponse);

////          ******************************  AUTHZFORCE ******************************
//            Response authzforceEvaluation = null;
//            try {
//                XmlUtils.XmlnsFilteringParser xacmlParserFactory = XacmlJaxbParsingUtils.getXacmlParserFactory(false).getInstance();
//                InputSource source = new InputSource(request.getXacmlRequest());
//                Object authzforceRequest = xacmlParserFactory.parse(source);
//                if(authzforceRequest instanceof Request) {
//                    authzforceEvaluation = authzforcePdp.evaluate((Request) authzforceRequest);
//                }
//            } catch (JAXBException e) {
//                e.printStackTrace();
//            }
//
//            assert authzforceEvaluation != null;

//
//            System.out.println("EVALUATED REQUEST.\nAUTHZFORCE OUTCOME: ");
//            System.out.println(authzforceEvaluation);

            AccessControlReply reply = AccessControlReply.newBuilder().setXacmlReply(balanaEvaluation).build();

            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    private static String toPrettyString (String xml) {
        try {
            // Turn xml string into a document
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));

            // Remove whitespaces outside tags
            document.normalize();
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.evaluate("//text()[normalize-space()='']",
                    document,
                    XPathConstants.NODESET);

            for (int i = 0; i < nodeList.getLength(); ++i) {
                Node node = nodeList.item(i);
                node.getParentNode().removeChild(node);
            }

            // Setup pretty print options
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
//            transformerFactory.setAttribute("indent-number", indent);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");

            // Return pretty print xml string
            StringWriter stringWriter = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
            return stringWriter.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
