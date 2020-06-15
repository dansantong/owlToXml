import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.*;

import java.io.File;

import javax.naming.Name;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;


import org.w3c.dom.Document;
import org.w3c.dom.Element;


public class main {
    public static void main(String args[]) throws FileNotFoundException, ParserConfigurationException, TransformerException {
        //提示用户输入待解析的owl文件的位置
        Scanner sc = new Scanner(System.in);
        System.out.println("Please input the location of the owl file:(E.g. c://Users//example.owl)\n");
        String location = sc.next();
        //根据输入的location加载owl文件
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        ontModel.read(new FileInputStream(location), "");
        //由用户输入namespace
        System.out.println("Please input the desired OPC UA Namespace:(E.g. motor)\n");
        String nameSpace = sc.next();
        // 创建xml解析器工厂
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = factory.newDocumentBuilder();
        Document document = db.newDocument();
        // 不显示standalone="no"
        document.setXmlStandalone(true);
        //xml文件头部必要的信息
        Element ModelDesign = document.createElement("ModelDesign");
        ModelDesign.setAttribute("xmlns:uax","http://opcfoundation.org/UA/2008/02/Types.xsd");
        ModelDesign.setAttribute("xmlns:xsi","http://www.w3.org/2001/XMLSchema-instance");
        ModelDesign.setAttribute("xmlns:ua","http://opcfoundation.org/UA/");
        ModelDesign.setAttribute("xmlns:"+nameSpace.toUpperCase(),"https://opcua.dansan/UA/"+nameSpace.toLowerCase()+"/");
        ModelDesign.setAttribute("xmlns:xsd","http://www.w3.org/2001/XMLSchema");
        ModelDesign.setAttribute("TargetNamespace","https://opcua.dansan/UA/"+nameSpace.toLowerCase()+"/");
        ModelDesign.setAttribute("TargetXmlNamespace","https://opcua.dansan/UA/"+nameSpace.toLowerCase()+"/");
        ModelDesign.setAttribute("TargetVersion","0.9.0");
        ModelDesign.setAttribute("TargetPublicationDate","2019-04-01T00:00:00Z");
        ModelDesign.setAttribute("xmlns","http://opcfoundation.org/UA/ModelDesign.xsd");

        //向ModelDesign根节点中添加子节点Namespaces
        Element Namespaces = document.createElement("Namespaces");
        ModelDesign.appendChild(Namespaces);

        //向Namespaces节点中添加节点Namespace
        Element Namespace1 = document.createElement("Namespace");
        Namespace1.setAttribute("Name",nameSpace.toLowerCase());
        Namespace1.setAttribute("Prefix",nameSpace.toLowerCase());
        Namespace1.setAttribute("XmlNamespace","https://opcua.dansan/UA/"+nameSpace.toLowerCase()+"/Types.xsd");
        Namespace1.setAttribute("XmlPrefix",nameSpace.toLowerCase());
        Namespace1.setTextContent("https://opcua.dansan/UA/"+nameSpace.toLowerCase()+"/");

        Element Namespace2 = document.createElement("Namespace");
        Namespace2.setAttribute("Name","OpcUa");
        Namespace2.setAttribute("Version","1.03");
        Namespace2.setAttribute("PublicationDate","2013-12-02T00:00:00Z");
        Namespace2.setAttribute("Prefix","Opc.Ua");
        Namespace2.setAttribute("InternalPrefix","Opc.Ua.Server");
        Namespace2.setAttribute("XmlNamespace","http://opcfoundation.org/UA/2008/02/Types.xsd");
        Namespace2.setAttribute("XmlPrefix","OpcUa");
        Namespace2.setTextContent("http://opcfoundation.org/UA/");

        Namespaces.appendChild(Namespace1);
        Namespaces.appendChild(Namespace2);
        //添加ObjectType
        Map<String,Element> clazz = new HashMap<>();
        for (Iterator<?> i = ontModel.listClasses(); i.hasNext();) {
            OntClass c = (OntClass) i.next(); // 返回类型强制转换
            if (!c.isAnon()) { // 如果不是匿名类，则打印类的名字
                System.out.print("Class:");
                System.out.println(c.getLocalName());
                Element ObjectType = document.createElement("ObjectType");
                ObjectType.setAttribute("SymbolicName",nameSpace.toUpperCase()+":"+c.getLocalName());
                ObjectType.setAttribute("BaseType","ua:BaseObjectType");
                ObjectType.setAttribute("SupportsEvents","true");
                clazz.put(c.getLocalName(),ObjectType);
                ModelDesign.appendChild(ObjectType);
            }
        }
        //向ObjectType下面添加Property
        Map<String,Element> dataProperty = new HashMap<>();
        for (Iterator<?> i = ontModel.listDatatypeProperties(); i.hasNext();) {
            DatatypeProperty dp = (DatatypeProperty)i.next();
            System.out.print("DataProperty:");
            System.out.println(dp.getLocalName());
            String name = dp.getLocalName();
            System.out.println("domain:"+dp.getDomain().getLocalName());
            String domain = dp.getDomain().getLocalName();
            Element ObjectType = clazz.get(domain);
            if(!dataProperty.containsKey(domain)){
                Element Children = document.createElement("Children");
                dataProperty.put(domain,Children);
                ObjectType.appendChild(Children);
            }
            Element Children = dataProperty.get(domain);
            Element Property = document.createElement("Property");
            Children.appendChild(Property);
            Property.setAttribute("SymbolicName",nameSpace.toUpperCase()+":"+name);
            if(dp.getRange()!=null){
                System.out.println("range:" + dp.getRange().getLocalName());
                String range = dp.getRange().getLocalName();
                //integer，double等Datatype都需要首字母大写
                Property.setAttribute("DataType","ua:"+range.substring(0, 1).toUpperCase() + range.substring(1));
            }
            Property.setAttribute("ValueRank","Scalar");
            Property.setAttribute("ModellingRule","Mandatory");

        }

        for (Iterator<?> i = ontModel.listObjectProperties(); i.hasNext();) {
            ObjectProperty op = (ObjectProperty) i.next();
            System.out.print("ObjectProperty:");
            System.out.println("localname:" + op.getLocalName());
            System.out.println("domain:"+op.getDomain().getLocalName());
            System.out.println("range:" + op.getRange().getLocalName());
            Element ReferenceType = document.createElement("ReferenceType");
            ReferenceType.setAttribute("SymbolicName",nameSpace.toUpperCase()+":"+op.getLocalName());
            ReferenceType.setAttribute("BaseType","ua:NonHierarchicalReferences");
            ReferenceType.setAttribute("Symmetric","true");
            ReferenceType.setAttribute("IsAbstract","false");
            ModelDesign.appendChild(ReferenceType);
        }

        // 将ModelDesign节点添加到dom树中
        document.appendChild(ModelDesign);
        // 创建TransformerFactory对象
        TransformerFactory tff = TransformerFactory.newInstance();
        // 创建 Transformer对象
        Transformer tf = tff.newTransformer();

        // 输出内容是否使用换行
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        // 输出内容使用缩进
        tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        // 创建xml文件并写入内容
        tf.transform(new DOMSource(document), new StreamResult(new File(nameSpace.toLowerCase()+".xml")));
        System.out.println("生成xml成功");
    }
}
