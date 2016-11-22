package hh.tools.analytics.httpd;

import hh.tools.file.ZipUtils;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Hugues on 07/03/2016.
 */
public class Application{


    static final String CSV_SEP = ";";


    static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    static final SimpleDateFormat dayLogsFormat = new SimpleDateFormat("YYYYMMdd");

    String appEnvrionnementFileName;

    File saslogDir;

    File targetDir;

    File backupDir;

    String dayDirName;

    String[] machines;

    String[] servicesHTTP;

    String[] servicesJAVA;

    String[] logNameJAVA;


    /**
     * LogFormat "%{%Y-%m-%d}t;%{%H:%M:%S}t;%s;%D;%b;%h;%m;%{Host}i;%U;%q;%{JSESSIONID}C;%{Cookie}n"
     * common
     */
    static final String[] columnsName= new String[]{"day", "hour", "response_status", "time_taken_serve", "response_bytes", "remote_adress", "request_verb", "request_host", "request_path", "request_params", "request_sessionid", "request_cookie", "server_name", "service_web", "request_clean_path", "request_type","OK", "actions", "day_minutes" };





    public static void main(String[] args) {
        try{

            // create Options object
            Options options = new Options();
            options.addOption("h", "help", false, " write help");
            options.addOption("c", "copySasLog", false, "copy sas log to local directory and unzip all files");
            options.addOption("d", "copyHttpPhpLog", false, "download log to local directory");
            options.addOption("a", "access", false, "aggregate httpd access log and add stats columns");
            options.addOption("w", "backweb", false, "aggregate backweb log ");
            options.addOption(
                Option.builder("t").longOpt("tomcat").hasArgs().argName("filter").desc("aggregate tomcat log").build());
            options.getOption("t").hasArgs();

            options.addOption("s", "stats", false, "aggregate httpd stats all days");
            options.addOption("b", "between", false,
                "calcul delta between strating a fonction and complete the page in Ajax");
            options.addOption(Option.builder("p")
                .longOpt("properties")
                .hasArg()
                .argName("filename")
                .desc("filePath du fichier de properties dans le classpath")
                .build());
            options.getOption("p").hasArg();


            // parse the command line arguments
            CommandLineParser parser = new DefaultParser();
            CommandLine line = parser.parse(options, args);

            // validate that block-size has been set
            if(line.hasOption("h")){
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("logs-analytics", options);
            }

            String pFileName = "newsesame_prod.properties";
            if(line.hasOption("p")){
                pFileName = line.getOptionValues("p")[0];
            }


            Application application;
            if(line.getArgs() != null && line.getArgs().length == 1){
                if(line.getArgs()[0].length() == 1){
                    // SI l'argument principal est de taille 1 on considere que c'est le nombre de jour
                    // d'ecart avec le lancement. 0 : Aujourd'hui
                    application = new Application(Integer.parseInt(line.getArgs()[0]), pFileName);
                }else {
                    // SINON c'est le nom du dossier dans la SAS LOG correspondant au jour sur lequel on
                    // souhaite travailler.
                    application = new Application(line.getArgs()[0], pFileName);
                }
            }else {
                // Si aucun argument global on travail sur J - 1 au niveau du SAS LOG
                application = new Application(1, pFileName);
            }
            // Deplace et decompress les traces de NewSesame.
            if(line.hasOption("b")){
                application.downloadTodayLogs();
            }

            // Deplace et decompress les traces de NewSesame.
            if(line.hasOption("c")){
                application.copyAndUnzipDayLogs();
            }
            // Aggrege les access log des deux machines et ajoute des colonnes facilitant les stats
            if(line.hasOption("a")){
                application.aggregateDayAccessLogHttpd();
            }

            // Aggrege toutes les LOG Tomcat dans un fichier trié par date
            if(line.hasOption("w")){
                for(String fileName : application.logNameJAVA){
                    application.aggregateDayBackEndLog(null, fileName);
                }
            }

            // Aggrege toutes les LOG Tomcat dans un fichier trié par date
            if(line.hasOption("t")){
                String[] tomcatArgs = line.getOptionValues("t");
                for(String fileName : application.logNameJAVA){
                    application.aggregateDayBackEndLog(tomcatArgs, fileName);
                }
            }

            // Aggrege toutes les LOG Tomcat dans un fichier trié par date
            if(line.hasOption("d")){
                application.deltaDayBackEndLog(new String[]{"AIGUILLAGE # Formatted URL : /frontend/dossier-client/",
                    "POST /api/dossierclient/_list : 200"});
            }

            // Genere un fichier global pour travailler sur toutes les stats en même temps.
            if(line.hasOption("s")){
                application.aggregateAllWithPrefix("aggrega-access-clean");
            }

        }catch(Exception e){
            System.out.println(" Exception " + e.getMessage());
            e.printStackTrace();
        }


    }

    /**
     * Constructeur de l'objet avec un entier
     *
     * @param beforeToday        interger under today
     * @param propertiesFileName
     * @throws IOException
     */
    Application(final int beforeToday, String propertiesFileName) throws IOException {

        initProperties(propertiesFileName);
        Calendar cal = Calendar.getInstance();
        cal.add(GregorianCalendar.DAY_OF_MONTH, -beforeToday);
        this.dayDirName = dayLogsFormat.format(cal.getTime());
        System.out.println(" Work on day : " + dayDirName);


    }

    /**
     * Constructuer avec un String correspondant au dossier du jour souhaité pattern YYYYMMdd
     *
     * @param useDayDirName
     * @param propertiesFileName
     * @throws IOException
     */
    Application(final String useDayDirName, String propertiesFileName) throws IOException {
        initProperties(propertiesFileName);
        this.dayDirName = useDayDirName;
        System.out.println(" Work on day : " + dayDirName);

    }

    /**
     * Initialise l'objet applicaiton avec le conf suprperties spécifié.
     *
     * @param propertiesFileName
     * @throws IOException
     */
    void initProperties(String propertiesFileName) throws IOException {
        Properties prop = new Properties();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propertiesFileName);
        prop.load(inputStream);

        this.appEnvrionnementFileName = propertiesFileName.split("/.")[0];

        this.saslogDir = new File(prop.getProperty("saslog.dir"));
        System.out.println("SASLOG  DIR : " + saslogDir);
        this.targetDir = new File(prop.getProperty("target.dir"));
        System.out.println("TARGET  DIR : " + targetDir);
        this.backupDir = new File(prop.getProperty("backup.dir"));
        System.out.println("BACKUP  DIR : " + backupDir);
        this.machines = prop.getProperty("machines").split(",");
        System.out.println("Machines : " + prop.getProperty("machines"));
        this.servicesHTTP = prop.getProperty("servicesHTTP").split(",");
        System.out.println("Services HTTP : " + prop.getProperty("servicesHTTP"));
        this.servicesJAVA = prop.getProperty("servicesJAVA").split(",");
        System.out.println("Services JAVA : " + prop.getProperty("servicesJAVA"));
        this.logNameJAVA = prop.getProperty("logNameJAVA").split(",");
        System.out.println("LogName JAVA : " + prop.getProperty("logNameJAVA"));
    }

    /**
     * Aggrege les log des services httpd pour un ensemble de fichier "aggrega-access-clean" jour pour
     * faire des statistiques
     *
     * @throws IOException
     */
    void aggregateAllWithPrefix(String prefixFileName) throws IOException {

        Collection<File>
            files =
            FileUtils.listFiles(backupDir, FileFilterUtils.prefixFileFilter(prefixFileName),
                FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        for(File file : files){
            System.out.println(" Find log file  " + file.getAbsolutePath());
            List<String> lines = FileUtils.readLines(file);
            for(String line : lines){
                nlines.add(line);
            }

        }

        File fSaved = FileUtils.getFile(backupDir, appEnvrionnementFileName + "-" + prefixFileName + "-all.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
            "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                + " lines ");
    }


    /**
     * Copie et decompresse les traces du SAS LOG pour permettre les analyses du jour sur un espace
     * local.
     *
     * @throws IOException
     */
    void copyAndUnzipDayLogs() throws IOException {


        ZipUtils unzip = new ZipUtils();
        File sasLogDirDay = FileUtils.getFile(saslogDir, dayDirName);

        for(String machine : machines){
            File machioneDir = FileUtils.getFile(sasLogDirDay, machine);
            System.out.println(" Read logs on " + machioneDir.getAbsolutePath());
            Collection<File>
                filesZip =
                FileUtils.listFiles(machioneDir, FileFilterUtils.suffixFileFilter(".zip"),
                    FileFilterUtils.directoryFileFilter());

            for(File file : filesZip){
                int dayPos = file.getAbsolutePath().indexOf(sasLogDirDay.getName());
                File unzipDir = new File(targetDir + "/" + file.getParent().substring(dayPos));
                System.out.println("unzip file " + file.getName() + " here : " + unzipDir.getAbsolutePath());
                unzipDir.mkdirs();

                try{
                    unzip.unzip(file.getAbsolutePath(), unzipDir.getAbsolutePath());
                }catch(IOException e){
                    e.printStackTrace();
                }
            }

            Collection<File>
                filesTxt =
                FileUtils.listFiles(machioneDir, FileFilterUtils.suffixFileFilter(".txt"),
                    FileFilterUtils.directoryFileFilter());

            for(File file : filesTxt){
                int dayPos = file.getAbsolutePath().indexOf(sasLogDirDay.getName());
                File copyDir = new File(targetDir + "/" + file.getParent().substring(dayPos));
                FileUtils.copyFileToDirectory(file, copyDir);

            }
        }


    }


    void downloadTodayLogs() throws IOException {

        /**
         * HttpGet httpGet = new HttpGet(
         * "https://applogscope.pacifica.group.gca/explorateur/ressource.php?id=data%2FProd%2FVL-C-PXX-34%20%28NEWSESAME%29%2Fhttpd%2Fhttpd-002%2Faccess_2016.06.05_log&orderby=nom&order=asc"
         * ); CredentialsProvider provider = new BasicCredentialsProvider(); UsernamePasswordCredentials
         * credentials = new UsernamePasswordCredentials(user, motDePasse);
         * provider.setCredentials(AuthScope.ANY, credentials); HttpClient client =
         * HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build(); HttpResponse
         * response = client.execute(httpGet); int statusCode =
         * response.getStatusLine().getStatusCode();
         **/


    }


    /**
     * Aggrege les traces Http d'une journée pour faire une analyse ponctuelle des erreurs et de la
     * performance de l'application
     *
     * @throws IOException
     */
    void aggregateDayAccessLogHttpd() throws IOException {

        File dayWorkDirectory = FileUtils.getFile(targetDir, dayDirName);

        Collection<File>
            files =
            FileUtils.listFiles(dayWorkDirectory, FileFilterUtils.prefixFileFilter("access"),
                FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        String csvHeader = ArrayUtils.toString(columnsName);
        nlines.add(csvHeader.replaceAll(",",";").substring(1,csvHeader.length() -1));

        for(File file : files){
            System.out.println(" Find access file  " + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file);
            for(String line : lines){

                if(!line.contains("/health-checks")){

                    StringBuilder strBuild = new StringBuilder(line);
                    strBuild.append(CSV_SEP);
                    for(String machine : machines){
                        if(file.getAbsolutePath().contains(machine)){
                            strBuild.append(machine).append(CSV_SEP);
                        }
                    }

                    for(String serviceHttp : servicesHTTP){
                        if(file.getAbsolutePath().contains(serviceHttp)){
                            strBuild.append(serviceHttp).append(CSV_SEP);
                        }
                    }

                    final String[] cLine = line.split(CSV_SEP);
                    // Ajout de l'URI Clean permettant de regrouper d'exclure les versions et les numéro
                    // fonctionnels.
                    strBuild.append(cleanUri(cLine[8])).append(CSV_SEP);
                    // Ajout type ressource Http
                    strBuild.append(typeUri(cLine[8])).append(CSV_SEP);
                    // Ajout sous Type
                    strBuild.append(sousType(cLine)).append(CSV_SEP);

                    // Ajout de l'Heure par tranche de 10 Min pour controler les dans le courant d'une journée.
                    strBuild.append(cLine[1].substring(0, 4)).append("0").append(CSV_SEP);

                    nlines.add(strBuild.toString());
                }
            }

        }

        File fSaved = FileUtils.getFile(backupDir, appEnvrionnementFileName + "-" + "aggrega-access-clean-" + dayDirName + ".csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
            "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                + " lines ");

    }

    private String sousType(String[] cLine) {
        int status = Integer.valueOf(cLine[2]);
        String verb = cLine[6];
        String sousType = "-";
        if(status == HttpStatus.SC_OK || status == HttpStatus.SC_NO_CONTENT || status == HttpStatus.SC_NOT_MODIFIED
            || status == HttpStatus.SC_NOT_FOUND || status == HttpStatus.SC_PARTIAL_CONTENT){
            if("GET".equalsIgnoreCase(verb)){
                sousType = "OK;Consultation";
            }else if("POST".equalsIgnoreCase(verb)){
                sousType = "OK;Modification";
            }
        }else if(status == HttpStatus.SC_CREATED){
            sousType = "OK;Creation";
        }else if( 399 < status && status < 499){
            if("GET".equalsIgnoreCase(verb)){
                sousType = "KO Fonctionnelle;Consultation";
            }else if("POST".equalsIgnoreCase(verb)){
                sousType = "KO Fonctionnelle;Modification/Creation";
            }

        }else if(499 < status ){
            if("GET".equalsIgnoreCase(verb)){
                sousType = "KO Technique;Consultation";
            }else if("POST".equalsIgnoreCase(verb)){
                sousType = "KO Technique;Modification/Creation";
            }


        }else {
            System.out.println("Attention status code non identifié pour le stats :" + status);
        }

        return sousType;

    }

    private String typeUri(String uri) {
        String[] uriDirs = uri.split("/");
        StringBuilder typeB = new StringBuilder();
        if(uriDirs.length > 1){
            String type = uriDirs[1];
            if("newsesame-adsu".equalsIgnoreCase(type)){
                type = "newsesame-back-web";
            }
            typeB.append(type).append("/");
        }
        return typeB.toString();
    }

    /**
     * Vérifie si la ligne de log est bien une trace Logback
     *
     * @param lineLogback
     * @return
     */
    Date matchDateLine(final String lineLogback) {
        Date dResult = null;
        try{
            if(lineLogback.length() > 15){
                String timeLine = lineLogback.substring(0, 13);
                if(StringUtils.isNotBlank(timeLine)){
                    dResult = dateFormat.parse(timeLine);
                }
            }
        }catch(ParseException e){
            // Not a patern with date, a stack trace or a file
        }
        return dResult;

    }


    /**
     * Aggrege les
     *
     * @param extractTexte
     * @param logName
     * @throws IOException
     */
    void aggregateDayBackEndLog(String[] extractTexte, String logName) throws IOException {

        final File dayWorkDirectory = FileUtils.getFile(targetDir, dayDirName);

        Collection<File>
            files =
            FileUtils.listFiles(dayWorkDirectory, FileFilterUtils.prefixFileFilter(logName),
                FileFilterUtils.directoryFileFilter());

        for(String serviceJAVA : servicesJAVA){
            List<LineLog> nlines = new ArrayList<LineLog>();
            for(File file : files){
                if(file.getPath().contains(serviceJAVA)){
                    if(extractTexte.length == 1) {
                        filterTomcatLogLinesWithExpression(extractTexte[0], nlines, file);
                    }else{
                        filterTomcatLogLines(extractTexte, nlines,file);
                    }
                }
            }
            writeBackEndAgregateLines(logName, serviceJAVA, extractTexte, nlines);
        }
    }

    /**
     * Ecrit le fichier aggrége des traces.
     *
     * @param logName
     * @param serviceTomcat
     * @param extractTexte
     * @param nlines
     * @throws IOException
     */
    void writeBackEndAgregateLines(String logName, String serviceTomcat, String[] extractTexte, List<LineLog> nlines)
        throws IOException {
        if(nlines.size() > 0){
            // Tri par date
            Collections.sort(nlines);

            // Enregistrement du resultat :
            StringBuilder buildFileName = new StringBuilder(appEnvrionnementFileName).append("-").append(serviceTomcat).append("-");
            buildFileName.append(logName).append("-").append(dayDirName);
            if(extractTexte != null && extractTexte.length > 0){
                buildFileName.append("-filter-")
                    .append(extractTexte[0].replaceAll(" ", "-").replaceAll(":", "").replaceAll("/", "_").replaceAll("\\\\",""));
            }
            buildFileName.append(".txt");
            File fSaved = FileUtils.getFile(targetDir, buildFileName.toString());
            FileUtils.writeLines(fSaved, nlines);
            System.out.println(
                "Ecriture du fichier  " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                    + " lines ");
        }else {
            System.out.println("Aucune ligne pour ce service " + serviceTomcat);
        }
    }


    void deltaDayBackEndLog(String[] deltaTexte) throws IOException {

        final File dayWorkDirectory = FileUtils.getFile(targetDir, dayDirName);

        Collection<File>
            files =
            FileUtils.listFiles(dayWorkDirectory, FileFilterUtils.prefixFileFilter("newsesame-back-web"),
                FileFilterUtils.directoryFileFilter());

        List<LineLog> nlines = new ArrayList<LineLog>();

        for(File file : files){
            filterTomcatLogLines(deltaTexte, nlines, file);
        }

        // Tri par date
        Collections.sort(nlines);

        Map<String, DeltaLine> nDelta = new HashMap<String, DeltaLine>();

        for(LineLog logLine : nlines){

            String[] logDatas = logLine._line.split(" - ");
            if(logDatas.length < 4){
                System.out.println(" Bad starting pattern, not a good logLine");
            }
            // Concatenation du User et du correlationId
            String key = logDatas[1] + ";" + logDatas[2];

            DeltaLine delta = nDelta.get(key);

            // Si la ligne contient le texte de depart
            if(logLine._line.contains(deltaTexte[0])){
                if(delta != null){
                    System.out.println(
                        "!!!  Strange 2 start for your start line on this user and correlationId, change the key ??????? "
                            + key);
                }
                nDelta.put(key, new DeltaLine(logLine));
            }
        }

        for(LineLog logLine : nlines){

            String[] logDatas = logLine._line.split(" - ");
            if(logDatas.length < 4){
                System.out.println(" Bad starting pattern, not a good logLine");
            }
            // Concatenation du User et du correlationId
            String key = logDatas[1] + ";" + logDatas[2];

            DeltaLine delta = nDelta.get(key);

            if(logLine._line.contains(deltaTexte[1])){
                if(delta == null){
                    System.out.println(
                        "!!!  Strange no start for your  end line on this user and correlationId, not a dossier client starting ?? "
                            + key);
                }else {
                    delta.putEndLogLine(logLine);
                }
            }
        }

        List<String> resultLines = new ArrayList<String>();
        resultLines.add("user;correlationId;startTime;downloadTime");
        for(String key : nDelta.keySet()){
            DeltaLine deltaLine = nDelta.get(key);
            StringBuilder lineBuilder = new StringBuilder();
            if(deltaLine.nextSessionEndLineLog.isEmpty()){
                System.out.println("Aucun element de fin pour la clés d'entrée : " + key);
                System.out.println("Aucun element de fin pour la ligne d'entrée : " + deltaLine._startLineLog._line);
            }else {
                Collections.sort(deltaLine.nextSessionEndLineLog);
                LineLog endLIne = deltaLine.nextSessionEndLineLog.get(0);
                if(endLIne == null){
                    System.out.println("Aucun element de fin pour la clés d'entrée : " + key);
                }else {

                    Long diffBetween = endLIne._dateTime.getTime() - deltaLine._startLineLog._dateTime.getTime();
                    lineBuilder.append(key)
                        .append(";")
                        .append(dateFormat.format(deltaLine._startLineLog._dateTime.getTime()))
                        .append(";")
                        .append(String.valueOf(diffBetween));
                    resultLines.add(lineBuilder.toString());
                }
            }

        }

        // Enregistrement du resultat :
        String fileName = "newsesame-back-web-" + dayDirName + ".csv";
        if(StringUtils.isNotEmpty(deltaTexte[0])){
            fileName =
                fileName.replaceFirst(".csv",
                    "-delta-" + deltaTexte[0].replaceAll(" ", "-").replaceAll(":", "").replaceAll("/", "_") + ".csv");
        }
        File fSaved = FileUtils.getFile(targetDir, fileName);
        FileUtils.writeLines(fSaved, resultLines);
        System.out.println(
            "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                + " lines ");

    }

    void filterTomcatLogLinesWithExpression(String regexp, List<LineLog> nlines, File file)throws IOException {
        System.out.println(" Find newsesame log file  " + file.getAbsolutePath());
        List<String> lines = FileUtils.readLines(file);

        Date preTime = null;
        boolean goodLine = true;

        Pattern pattern = null;

        if(regexp != null && regexp.length() > 0){
            goodLine = false;
            pattern = Pattern.compile(regexp);
        }

        for(String line : lines){

            //extractFinaxyCSV(nlines, file, line);
            Date dateLine = matchDateLine(line);
            if(!line.contains("pacifica.ns.web.filter.LoggingFilter  -  -  - GET /health-checks")){
                if(dateLine != null){
                    preTime = dateLine;
                    if(regexp != null && regexp.length() > 0) {
                        goodLine = false;

                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            goodLine = true;
                        }
                    }
                }else {
                    dateLine = preTime;
                }
                if(goodLine){
                    nlines.add(new LineLog(dateLine, line, file.getParentFile().getName()));

                }
            }

        }
    }


    /**
     * Lit les traces d'un fichier de trace Tomcat et ajoute un objet ligne de log dans la liste passé
     * en paramètre.
     *
     * @param extractTexte
     * @param nlines
     * @param file
     * @throws IOException
     */
    void filterTomcatLogLines(String[] extractTexte, List<LineLog> nlines, File file) throws IOException {
        System.out.println(" Find newsesame log file  " + file.getAbsolutePath());
        List<String> lines = FileUtils.readLines(file);

        Date preTime = null;
        boolean goodLine = true;

        if(extractTexte != null && extractTexte.length > 0){
            goodLine = false;
        }
        for(String line : lines){

            //extractFinaxyCSV(nlines, file, line);
            Date dateLine = matchDateLine(line);
            if(!line.contains("pacifica.ns.web.filter.LoggingFilter  -  -  - GET /health-checks")){
                if(dateLine != null){
                    preTime = dateLine;
                    if(extractTexte != null && extractTexte.length > 0){
                        goodLine = false;
                        for(String extract : extractTexte){
                            if(line.contains(extract)){
                                goodLine = true;
                            }
                        }
                    }
                }else {
                    dateLine = preTime;
                }
                if(goodLine){
                    nlines.add(new LineLog(dateLine, line, file.getParentFile().getName()));

                }
            }

        }
    }

    private void extractFinaxyCSV(List<LineLog> nlines, File file, String line) {
        /** Read Payload SOAP FINAXY for extract Users ans profile. **/

        if(line.startsWith("Payload: ")){
            StringBuilder bLine = new StringBuilder();
            // User :
            bLine.append(extractXmlValueFromString(line, "<desk>", 3))
                .append(CSV_SEP)
                .append(extractXmlValueFromString(line, "<identificationNumber>", 7))
                .append(CSV_SEP)
                .append(extractXmlValueFromString(line, "<profile>", 3))
                .append(CSV_SEP)
                .append(extractXmlValuesFromString(line, "<product><code>", 2))
                .append(CSV_SEP);

            String finaxyLine = bLine.toString();
            if(finaxyLine.length() > 7){
                nlines.add(new LineLog(new Date(), finaxyLine, file.getParentFile().getName()));
            }

        }
    }

    static String extractXmlValueFromString(String xml, String nodeName, int size) {
        int indexNodeName = xml.lastIndexOf(nodeName);
        int startNodeValue = indexNodeName + nodeName.length();
        int endNodeValue = startNodeValue + size;
        if(indexNodeName > 1 && xml.length() > endNodeValue){
            return xml.substring(startNodeValue, endNodeValue);
        }else {
            return "";
        }
    }

    static String extractXmlValuesFromString(String xml, String nodeName) {
        String[] tabNode = xml.split(escapeXmlCaracteres(nodeName));
        String values = "";
        for(int a =2; a < tabNode.length; a = a+2){
            if(values.length()>0){
                values = values + "|";
            }
            values = values + escapeXmlCaracteres(tabNode[a-1]);
        }
        return values;
    }

    static String extractXmlAttributeValueFrom(String xml, String attributeName, int sizeValue){
        String[] tabNode = xml.split(attributeName);
        String values = "";

        for(int a =1 ; a < tabNode.length; a++){
            if(values.length()>0){
                values = values + "|";
            }
            values = values + tabNode[a].substring(1 + 1 ,sizeValue + 1 + 1);
        }
        return values;


    }

    static String escapeXmlCaracteres(String xml){
        return xml.replaceAll("<","").replaceAll("/","").replaceAll(">","");
    }

    static String extractXmlValuesFromString(String xml, String nodeName, int size) {
        String[] indexNodeName = xml.split(nodeName);
        StringBuilder codeProduits = new StringBuilder();

        for(int i = 1; i < indexNodeName.length; i++){
            codeProduits.append(indexNodeName[i].substring(0, size)).append(",");
        }
        codeProduits.append(CSV_SEP).append(indexNodeName.length - 1);
        return codeProduits.toString();
    }


    /**
     * Nettoyage de la chaine de caactere URI pour arriver à une chaine unique sans cas fonctionnel.
     *
     * @param uri
     * @return
     */
    String cleanUri(String uri) {
        String cleanUri = uri;
        // Identifiant IBAN :
        cleanUri = cleanUriAfterSequence(cleanUri, "/iban/");
        // Recherche aaa:
        cleanUri = cleanUriAfterSequence(cleanUri, "/referentiel/aaa/v1/vehicule/");
        cleanUri = cleanUriAfterSequence(cleanUri, "/referentiel/aaa/v1/vehicules/");
        // Numero de versions application front
        cleanUri = cleanUri.replaceFirst("\\d{1,}[.]\\d{1,}[.]\\d{1,}", "x.y.z");
        // Numero de proposition
        cleanUri = cleanUri.replaceFirst("\\d{11}[V]\\d{3}", "{###V#}");
        // Remplacement des numéros swift : 10, devis et contrats : 15, PDF jusqu'a 40
        cleanUri = cleanUri.replaceFirst("\\d{10,40}", "#####");

        // Referentiel véhicule :
        if(cleanUri.contains("/referentiel/marques")){
            cleanUri = cleanUri.replaceAll("[/][A-Z]{2}[/]", "/{CODE}/");
            cleanUri = cleanUri.replaceAll("[/]\\d{2}[/]", "/{CODE}/");
        }
        return cleanUri;
    }

    /**
     * Nettoyage d'une fin d'URI, et replacement par #
     *
     * @param uri
     * @param seq
     * @return
     */
    String cleanUriAfterSequence(String uri, String seq) {
        String cleanUri = uri;
        int indexSeq = uri.lastIndexOf(seq);
        if(indexSeq > 0){
            cleanUri = uri.substring(0, indexSeq + seq.length()) + "#";
        }
        return cleanUri;
    }

    /**
     * Object representant une ligne de log pour permettre le tri par date
     */
    class LineLog implements Comparable<LineLog>{

        LineLog(Date dateTime, String line, String folder) {

            _dateTime = dateTime;
            _line = line;
            _folder = folder;

        }

        Date   _dateTime;
        String _line;
        String _folder;

        /**
         * @return string CSV with ';' separator
         */
        public String toString() {
            return _line.replaceAll(";", "|") + ";" + dateFormat.format(_dateTime) + ";" + _folder;
        }

        public int compareTo(LineLog o) {
            return _dateTime.compareTo(o._dateTime);
        }
    }


    /**
     * Objet permettant de stocker des lignes concernant un evenement.
     */
    class DeltaLine{

        DeltaLine(LineLog startLineLog) {
            _startLineLog = startLineLog;
        }

        public void putEndLogLine(LineLog endLineLog) {
            nextSessionEndLineLog.add(endLineLog);
        }

        LineLog _startLineLog;

        List<LineLog> nextSessionEndLineLog = new ArrayList<LineLog>();

    }


}
