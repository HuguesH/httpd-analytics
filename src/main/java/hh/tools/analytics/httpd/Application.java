package hh.tools.analytics.httpd;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import hh.tools.file.ZipUtils;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;

import javax.sound.sampled.Line;

/**
 * Created by Hugues on 07/03/2016.
 */
public class Application {


    static final String CSV_SEP = ";";

    static final String[] machines = new String[]{"vl-c-pxx-33", "vl-c-pxx-34"};

    static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    static final SimpleDateFormat dayLogsFormat = new SimpleDateFormat("YYYYMMdd");

    private File saslogDir;

    private File targetDir;

    private File backupDir;

    private String dayDirName;


    /**
     * LogFormat "%{%Y-%m-%d}t;%{%H:%M:%S}t;%s;%D;%b;%h;%m;%{Host}i;%U;%q;%{JSESSIONID}C;%{Cookie}n"
     * common
     */
    String columnName =
            "Jour;Heure;HttpStatus;Duree;Bytes;IP;Method;Host;URI;QueryParams;serveur;service;cleanURI;HH";


    public static void main(String[] args) {
        try {

            // create Options object
            Options options = new Options();
            options.addOption("h", "help", false, " write help");
            options.addOption("c", "copySasLog", false, "copy sas log to local directory and unzip all files");
            options.addOption("a", "access", false, "aggregate httpd access log and add stats columns");
            options.addOption("t", "tomcat", false, "aggregate tomcat log");
            options.addOption("s", "stats", false, "aggregate httpd stats all days");
            options.addOption("d", "strartDownload", false, "calcul delta between strating a fonction and complete tthe page in Ajax");
            //options.addOption(Option.builder().argName("correlationId").hasArg().desc("Correlation id seq").build());


            // parse the command line arguments
            CommandLineParser parser = new DefaultParser();
            CommandLine line = parser.parse(options, args);

            // validate that block-size has been set
            if (line.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("logs-analytics", options);
            }

            Application application = new Application("20160323");
            //Deplace et decompress les traces de NewSesame.
            if (line.hasOption("c")) {
                application.copyAndUnzipDayLogs();
            }
            //Aggrege les access log des deux machines et ajoute des colonnes facilitant les stats
            if (line.hasOption("a")) {
                application.aggregateDayAccessLogHttpd();
            }

            //Aggrege toutes les LOG Tomcat dans un fichier trié par date
            if (line.hasOption("t")) {
                application.aggregateDayBackEndLog(new String[]{"f85afe24-57a3-4841-bc71-90fb9072a1a5"});
            }

            //Aggrege toutes les LOG Tomcat dans un fichier trié par date
            if (line.hasOption("d")) {
                application.deltaDayBackEndLog(new String[]{"AIGUILLAGE # Formatted URL : /frontend/dossier-client/index-cl.html#/home", "recupèration de la liste des notifications .  (DossierClientRestController.java:140)"});
            }

            //Genere un fichier global pour travailler sur toutes les stats en même temps.
            if (line.hasOption("s")) {
                application.aggregateAllAccessLogHttpd();
            }

        } catch (Exception e) {
            System.out.println(" Exception " + e.getMessage());
            e.printStackTrace();
        }


    }

    public Application(final int beforeToday) throws IOException {

        initProperties();
        Calendar cal = Calendar.getInstance();
        cal.add(GregorianCalendar.DAY_OF_MONTH, -beforeToday);
        this.dayDirName = dayLogsFormat.format(cal.getTime());
        System.out.println(" Work on day : " + dayDirName);


    }

    public Application(final String useDayDirName) throws IOException {
        initProperties();
        this.dayDirName = useDayDirName;
        System.out.println(" Work on day : " + dayDirName);

    }

    private void initProperties() throws IOException {
        Properties prop = new Properties();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("app_home.properties");
        prop.load(inputStream);

        this.saslogDir = new File(prop.getProperty("saslog.dir"));
        System.out.println("SASLOG  DIR : " + saslogDir);
        this.targetDir = new File(prop.getProperty("target.dir"));
        System.out.println("TARGET  DIR : " + targetDir);
        this.backupDir = new File(prop.getProperty("backup.dir"));
        System.out.println("BACKUP  DIR : " + backupDir);
    }

    private void aggregateAllAccessLogHttpd() throws IOException {

        Collection<File> files = FileUtils.listFiles(backupDir, FileFilterUtils.prefixFileFilter("aggrega-access-clean"), FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        for (File file : files) {
            System.out.println(" Find log file  " + file.getAbsolutePath());
            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                nlines.add(line);
            }

        }

        File fSaved = FileUtils.getFile(backupDir, "aggrega-access-all.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size()) + " lines ");
    }


    private void copyAndUnzipDayLogs() throws IOException {


        ZipUtils unzip = new ZipUtils();
        File sasLogDirDay = FileUtils.getFile(saslogDir, dayDirName);

        for (String machine : machines) {
            File machioneDir = FileUtils.getFile(sasLogDirDay, machine);
            System.out.println(" Read logs on " + machioneDir.getAbsolutePath());
            Collection<File> files = FileUtils.listFiles(machioneDir, FileFilterUtils.suffixFileFilter(".zip"),
                    FileFilterUtils.directoryFileFilter());

            for (File file : files) {
                int dayPos = file.getAbsolutePath().indexOf(sasLogDirDay.getName());
                File unzipDir = new File(targetDir + "/" + file.getParent().substring(dayPos));
                System.out.println("unzip file " + file.getName() + " here : " + unzipDir.getAbsolutePath());
                unzipDir.mkdirs();

                try {
                    unzip.unzip(file.getAbsolutePath(), unzipDir.getAbsolutePath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


    }


    private void aggregateDayAccessLogHttpd() throws IOException {

        File dayWorkDirectory = FileUtils.getFile(targetDir, dayDirName);

        Collection<File> files = FileUtils.listFiles(dayWorkDirectory, FileFilterUtils.prefixFileFilter("access"),
                FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        nlines.add(columnName.toString().replace(',', ';'));

        for (File file : files) {
            System.out.println(" Find access file  " + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {

                if (!line.contains("/health-checks")) {

                    // @TODO demander a CAAGIS de remplacer le header COOKIE par le correlationId
                    // @TODO demander a CAAGIS de mettre le header Location
                    StringBuilder strBuild = new StringBuilder(line.replaceAll("-;-", ""));

                    if (file.getAbsolutePath().contains("vl-c-pxx-33")) {
                        strBuild.append("vl-c-pxx-33").append(CSV_SEP);
                    }
                    if (file.getAbsolutePath().contains("vl-c-pxx-34")) {
                        strBuild.append("vl-c-pxx-34").append(CSV_SEP);
                    }
                    if (file.getAbsolutePath().contains("httpd-001")) {
                        strBuild.append("httpd-001").append(CSV_SEP);
                    }
                    if (file.getAbsolutePath().contains("httpd-002")) {
                        strBuild.append("httpd-002").append(CSV_SEP);
                    }

                    final String[] cLine = line.split(CSV_SEP);
                    //Ajout de l'URI Clean permettant de regrouper d'exclure les versions et les numéro fonctionnels.
                    strBuild.append(cleanUri(cLine[8])).append(CSV_SEP);
                    //Ajout de l'Heure pour contrler le flux dans la suite de la journée.
                    strBuild.append(cLine[1].substring(0, 2)).append(CSV_SEP);

                    nlines.add(strBuild.toString());
                }
            }

        }

        File fSaved = FileUtils.getFile(backupDir, "aggrega-access-clean-" + dayDirName + ".csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size()) + " lines ");

    }

    private Date matchDateLine(final String lineLogback) {
        Date dResult = null;
        try {
            if (lineLogback.length() > 15) {
                String timeLine = lineLogback.substring(0, 13);
                if (StringUtils.isNotBlank(timeLine)) {
                    dResult = dateFormat.parse(timeLine);
                }
            }
        } catch (ParseException e) {
            //Not a patern with date, a stack trace or a file
        }
        return dResult;

    }


    private void aggregateDayBackEndLog(String[] extractTexte) throws IOException {

        final File dayWorkDirectory = FileUtils.getFile(targetDir, dayDirName);

        Collection<File> files = FileUtils.listFiles(dayWorkDirectory,
                FileFilterUtils.prefixFileFilter("newsesame-back-web"), FileFilterUtils.directoryFileFilter());

        List<LineLog> nlines = new ArrayList<LineLog>();

        for (File file : files) {
            filterTomcatLogLines(extractTexte, nlines, file);
        }

        // Tri par date
        Collections.sort(nlines);

        //Enregistrement du resultat :
        String fileName = "newsesame-back-web-" + dayDirName + ".csv";
        if (StringUtils.isNotEmpty(extractTexte[0])) {
            fileName = fileName.replaceFirst(".csv", "-filter-" + extractTexte[0].replaceAll(" ", "-").replaceAll(":", "").replaceAll("/","_").substring(0,13) + ".csv");
        }
        File fSaved = FileUtils.getFile(targetDir, fileName);
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size()) + " lines ");

    }


    private void deltaDayBackEndLog(String[] deltaTexte) throws IOException {

        final File dayWorkDirectory = FileUtils.getFile(targetDir, dayDirName);

        Collection<File> files = FileUtils.listFiles(dayWorkDirectory,
                FileFilterUtils.prefixFileFilter("newsesame-back-web"), FileFilterUtils.directoryFileFilter());

        List<LineLog> nlines = new ArrayList<LineLog>();

        for (File file : files) {
            filterTomcatLogLines(deltaTexte, nlines, file);
        }

        // Tri par date
        Collections.sort(nlines);

        Map<String, DeltaLine> nDelta = new HashMap<String, DeltaLine>();

        for (LineLog logLine : nlines) {

            String[] logDatas = logLine._line.split(" - ");
            if (logDatas.length < 4) {
                System.out.println(" Bad starting pattern, not a good logLine");
            }
            // Concatenation du User et du correlationId
            String key = logDatas[1] + ";" + logDatas[2];

            DeltaLine delta = nDelta.get(key);

            //Si la ligne contient le texte de depart
            if (logLine._line.contains(deltaTexte[0])) {
                if (delta != null) {
                    System.out.println("!!!  Strange 2 start for your start line on this user and correlationId, change the key ??????? " + key);
                }
                nDelta.put(key, new DeltaLine(logLine));
            }
        }

        for (LineLog logLine : nlines) {

            String[] logDatas = logLine._line.split(" - ");
            if (logDatas.length < 4) {
                System.out.println(" Bad starting pattern, not a good logLine");
            }
            // Concatenation du User et du correlationId
            String key = logDatas[1] + ";" + logDatas[2];

            DeltaLine delta = nDelta.get(key);

            if (logLine._line.contains(deltaTexte[1])) {
                if (delta == null) {
                    System.out.println("!!!  Strange no start for your  end line on this user and correlationId, not a dossier client starting ?? " + key);
                }else {
                    delta.putEndLogLine(logLine);
                }
            }
        }

        List<String> resultLines = new ArrayList<String>();
        resultLines.add("user;correlationId;startTime;downloadTime");
        for (String key : nDelta.keySet()) {
            DeltaLine deltaLine = nDelta.get(key);
            StringBuilder lineBuilder = new StringBuilder();
            if (deltaLine.nextSessionEndLineLog.isEmpty()) {
                System.out.println("Aucun element de fin pour la clés d'entrée : " + key);
                System.out.println("Aucun element de fin pour la ligne d'entrée : " + deltaLine._startLineLog._line);
            } else {
                Collections.sort(deltaLine.nextSessionEndLineLog);
                LineLog endLIne = deltaLine.nextSessionEndLineLog.get(0);
                if (endLIne == null) {
                    System.out.println("Aucun element de fin pour la clés d'entrée : " + key);
                } else {

                    Long diffBetween = endLIne._dateTime.getTime() - deltaLine._startLineLog._dateTime.getTime();
                    lineBuilder.append(key).append(";").append(dateFormat.format(deltaLine._startLineLog._dateTime.getTime())).append(";").append(String.valueOf(diffBetween));
                    resultLines.add(lineBuilder.toString());
                }
            }

        }

        //Enregistrement du resultat :
        String fileName = "newsesame-back-web-" + dayDirName + ".csv";
        if (StringUtils.isNotEmpty(deltaTexte[0])) {
            fileName = fileName.replaceFirst(".csv", "-delta-" + deltaTexte[0].replaceAll(" ", "-").replaceAll(":", "").replaceAll("/","_") + ".csv");
        }
        File fSaved = FileUtils.getFile(targetDir, fileName);
        FileUtils.writeLines(fSaved, resultLines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size()) + " lines ");

    }


    private void filterTomcatLogLines(String[] extractTexte, List<LineLog> nlines, File file) throws IOException {
        System.out.println(" Find newsesame-back-web log file  " + file.getAbsolutePath());
        List<String> lines = FileUtils.readLines(file);

        Date preTime = null;
        boolean goodLine = true;

        if (extractTexte != null && extractTexte.length > 0) {
            goodLine = false;
        }
        for (String line : lines) {
            Date dateLine = matchDateLine(line);
            if (dateLine != null) {
                preTime = dateLine;
                if (extractTexte != null && extractTexte.length > 0) {
                    goodLine = false;
                    for (String extract : extractTexte) {
                        if (line.contains(extract)) {
                            goodLine = true;
                        }
                    }
                }
            } else {
                dateLine = preTime;
            }
            if (goodLine) {
                nlines.add(new LineLog(dateLine, line, file.getParentFile().getName()));
            }
        }
    }


    /**
     * Nettoyage de la chaine de caactere URI pour arriver à une chaine uniqu sans cas fonctionnel.
     *
     * @param uri
     * @return
     */
    private String cleanUri(String uri) {
        String cleanUri = uri;
        // Identifiant IBAN :
        cleanUri = cleanUriAfterSequence(cleanUri, "/iban/");
        // Recherche aaa:
        cleanUri = cleanUriAfterSequence(cleanUri, "/referentiel/aaa/v1/vehicule/");
        cleanUri = cleanUriAfterSequence(cleanUri, "/referentiel/aaa/v1/vehicules/");
        // Numero de versions application front
        cleanUri = cleanUri.replaceFirst("\\d{1}[.]\\d{1}[.]\\d{2}", "x.y.z");
        // Numero de proposition
        cleanUri = cleanUri.replaceFirst("\\d{11}[V]\\d{3}", "{###V#}");
        // Remplacement des numéros swift : 10, devis et contrats : 15, PDF jusqu'a 40
        cleanUri = cleanUri.replaceFirst("\\d{10,40}", "#####");

        // Referentiel véhicule :
        if (cleanUri.contains("/referentiel/marques")) {
            cleanUri = cleanUri.replaceAll("[/][A-Z]{2}[/]", "/{CODE}/");
            cleanUri = cleanUri.replaceAll("[/]\\d{2}[/]", "/{CODE}/");
        }
        return cleanUri;
    }

    private String cleanUriAfterSequence(String uri, String seq) {
        String cleanUri = uri;
        int indexSeq = uri.lastIndexOf(seq);
        if (indexSeq > 0) {
            cleanUri = uri.substring(0, indexSeq + seq.length()) + "#";
        }
        return cleanUri;
    }

    /**
     * Object representant une ligne de log pour permettre le tri par date
     */
    private class LineLog implements Comparable<LineLog> {

        LineLog(Date dateTime, String line, String folder) {

            _dateTime = dateTime;
            _line = line;
            _folder = folder;

        }

        Date _dateTime;
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
     *
     */
    private class DeltaLine {

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
