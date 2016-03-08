package hh.tools.analytics.httpd;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Created by Hugues on 07/03/2016.
 */
public class Application {


    static final String CSV_SEP = ";";

    static final String[] machines = new String[]{"vl-c-pxx-33", "vl-c-pxx-34"};

    static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    static final SimpleDateFormat dayLogsFormat = new SimpleDateFormat("YYYYMMdd");

    private String saslogDir;

    private String targetDir;

    private String backupDir;


    /**
     * LogFormat "%{%Y-%m-%d}t;%{%H:%M:%S}t;%s;%D;%b;%h;%m;%{Host}i;%U;%q;%{JSESSIONID}C;%{Cookie}n" common
     */
    String columnName = "Jour;Heure;HttpStatus;Duree;Bytes;IP;Method;Host;URI;QueryParams;serveur;service;cleanURI";


    public static void main(String[] args) {
        try {
            Application application = new Application();
            String dayDir = application.copyAndUnzipDayLogs();
            File dayDirF = new File(dayDir);
            application.aggregateAccessLogHttpd(dayDirF);
            application.cleanAccessLogHttpd(dayDirF);

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    Application() throws IOException {
        Properties prop = new Properties();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("app_home.properties");
        prop.load(inputStream);

        saslogDir = prop.getProperty("saslog.dir");
        System.out.println("SASLOG  DIR : " + saslogDir);

        targetDir = prop.getProperty("target.dir");
        backupDir = prop.getProperty("backup.dir");

    }


    private String copyAndUnzipDayLogs() throws IOException {
        UnzipUtility unzip = new UnzipUtility();
        Calendar cal = Calendar.getInstance();
        cal.add(GregorianCalendar.DAY_OF_MONTH, -4);
        String dayDirectory = dayLogsFormat.format(cal.getTime());

        for (String machine : machines) {
            StringBuilder sasLogDay = new StringBuilder(saslogDir);
            sasLogDay.append("/").append(dayDirectory).append("/").append(machine);
            File sasLogDayF = new File(sasLogDay.toString());
            if (sasLogDayF.isDirectory()) {

                System.out.println(" Work on " + sasLogDayF.toString());

                Collection<File> files = FileUtils.listFiles(sasLogDayF, FileFilterUtils.suffixFileFilter(".zip"),
                        FileFilterUtils.directoryFileFilter());

                for (File file : files) {
                    int dayPos = file.getAbsolutePath().lastIndexOf(dayDirectory);
                    File unzipDir = new File(targetDir + "/" + file.getParent().substring(dayPos));
                    System.out.println("unzip file" + file.getName() + " here : " + unzipDir.getAbsolutePath());
                    unzipDir.mkdirs();

                    try {
                        unzip.unzip(file.getAbsolutePath(), unzipDir.getAbsolutePath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                System.out.println(" Not a valid directory " + sasLogDayF.toString());
            }

        }

        return targetDir + "/" + dayDirectory;


    }

    private void aggregateAccessLogHttpd(File dayWorkDirectory) throws IOException {

        Collection<File> files = FileUtils.listFiles(dayWorkDirectory, FileFilterUtils.prefixFileFilter("access"),
                FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();

        for (File file : files) {
            System.out.println(" Find log file  " + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                // @TODO demander a CAAGIS de remplacer le header COOKIE par le correlationId
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
                nlines.add(strBuild.toString());
            }


        }

        File fSaved = FileUtils.getFile(dayWorkDirectory, "aggrega-access.csv");
        FileUtils.writeLines(fSaved, nlines);

    }

    private void aggregateBackEndLogHttpd(File dayWorkDirectory) throws IOException {

        Collection<File> files = FileUtils.listFiles(dayWorkDirectory, FileFilterUtils.prefixFileFilter("newsesame-back-web"),
                FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        nlines.add("log;time;tomcat");

        for (File file : files) {
            System.out.println(" Find newsesame-back-web log file  " + file.getAbsolutePath());
            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                if (line.contains("INFO  pacifica.ns.web.filter.LoggingFilter")) {
                    StringBuilder strBuild = new StringBuilder(line.replaceAll(";", "%"));
                    String time = line.substring(0, 13);
                    strBuild.append(CSV_SEP).append(time).append(CSV_SEP);
                    if (file.getAbsolutePath().contains("CA_TC01")) {
                        strBuild.append("CA_TC01").append(CSV_SEP);
                    }
                    if (file.getAbsolutePath().contains("CA_TC02")) {
                        strBuild.append("CA_TC02").append(CSV_SEP);
                    }
                    if (file.getAbsolutePath().contains("CA_TC03")) {
                        strBuild.append("CA_TC03").append(CSV_SEP);
                    }
                    if (file.getAbsolutePath().contains("CA_TC04")) {
                        strBuild.append("CA_TC04").append(CSV_SEP);
                    }
                    nlines.add(strBuild.toString());
                }
            }
        }

        File fSaved = FileUtils.getFile(dayWorkDirectory, "aggrega-newsesame-back-web.csv");
        FileUtils.writeLines(fSaved, nlines);

    }

    /**
     * Enregistrement de la collection des sessions dans un fichier CSV dont les noms de colonne sont decrites
     * dans l'enumere ColumnName
     *
     * @throws IOException
     */
    private void saveResultsInfile() throws IOException {
        //TODO

    }

    /**
     * Extrait les informations souhait� pour une recherche dans un fichier de log.
     *
     * @throws IOException
     */
    private void aggregateBackWeb(File dayWorkDirectory, String apiURI) throws IOException, ParseException {

        Collection<File> files = FileUtils.listFiles(dayWorkDirectory, FileFilterUtils.prefixFileFilter("aggrega-newsesame-back-web"),
                FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        nlines.add("URI;thread;startTime;endTime;delta;");
        Map<String, String> mUri = new HashMap<String, String>();

        for (File file : files) {
            System.out.println(" Find newsesame-back-web log file  " + file.getAbsolutePath());
            List<String> lines = FileUtils.readLines(file);

            for (String line : lines) {
                //Analyse the line
                if (line.contains(apiURI)) {
                    //Start
                    if (line.contains(apiURI + "  (")) {
                        String startTime = line.substring(0, 13);
                        String thread = line.substring(14, line.indexOf("] INFO"));
                        if (mUri.get(thread) != null) {
                            System.out.println(" WARN : thread is use !!!!!");
                        } else {
                            mUri.put(thread, startTime);
                        }


                    }
                    //End
                    if (line.contains(apiURI + " :")) {
                        String endTime = line.substring(0, 13);
                        String thread = line.substring(14, line.indexOf("] INFO"));
                        String startTime = mUri.remove(thread);
                        if (startTime == null) {
                            System.out.println(" WARN : thread not initialise ");
                        } else {
                            StringBuilder csvLine = new StringBuilder(apiURI);

                            Date startD = dateFormat.parse(startTime);
                            Date endD = dateFormat.parse(endTime);

                            long diff = endD.getTime() - startD.getTime();

                            csvLine.append(CSV_SEP).append(thread).append(CSV_SEP).append(startTime).append(CSV_SEP).append(endTime).append(CSV_SEP).append(diff).append(CSV_SEP);
                            nlines.add(csvLine.toString());
                        }

                    }
                }

            }

        }

        File fSaved = FileUtils.getFile(dayWorkDirectory, apiURI.replaceAll("/", "") + ".csv");
        System.out.println("Saved in file : " + fSaved.getAbsolutePath());
        FileUtils.writeLines(fSaved, nlines);

    }


    /**
     * Extrait les informations souhait� pour une recherche dans un fichier de log.
     *
     * @throws IOException
     */
    private void cleanAccessLogHttpd(File dayWorkDirectory) throws IOException {
        Collection<File> files = FileUtils.listFiles(dayWorkDirectory, FileFilterUtils.prefixFileFilter("aggrega-access"),
                FileFilterUtils.directoryFileFilter());
        for (File file : files) {
            System.out.println(" Find acces log file  " + file.getAbsolutePath());
            List<String> lines = FileUtils.readLines(file);
            List<String> nlines = new ArrayList<String>();
            nlines.add(columnName.toString().replace(',', ';'));
            for (String line : lines) {
                if (!line.contains("/health-checks")) {
                    String[] cLine = line.split(CSV_SEP);
                    String uri = cLine[8];
                    StringBuilder newLine = new StringBuilder(line).append(cleanUri(uri));
                    nlines.add(newLine.toString());
                }
            }
            FileUtils.writeLines(FileUtils.getFile(dayWorkDirectory, "aggrega-access-clean.csv"), nlines);
        }
    }

    private String cleanUri(String uri) {
        String cleanUri = uri;
        //Identifiant IBAN :
        cleanUri = cleanUriAfterSequence(cleanUri, "/iban/");
        //Recherche aaa:
        cleanUri = cleanUriAfterSequence(cleanUri, "/referentiel/aaa/v1/vehicule/");
        cleanUri = cleanUriAfterSequence(cleanUri, "/referentiel/aaa/v1/vehicules/");
        //Numero de versions application front
        cleanUri = cleanUri.replaceFirst("\\d{1}[.]\\d{1}[.]\\d{2}", "x.y.z");
        //Numero de proposition
        cleanUri = cleanUri.replaceFirst("\\d{11}[V]\\d{3}", "{###V#}");
        //Remplacement des numéros swift : 10, devis et contrats : 15, PDF jusqu'a 40
        cleanUri = cleanUri.replaceFirst("\\d{10,40}", "#####");

        //Referentiel véhicule :
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

    public class UnzipUtility {
        /**
         * Size of the buffer to read/write data
         */
        private static final int BUFFER_SIZE = 4096;

        /**
         * Extracts a zip file specified by the zipFilePath to a directory specified by
         * destDirectory (will be created if does not exists)
         *
         * @param zipFilePath
         * @param destDirectory
         * @throws IOException
         */
        public void unzip(String zipFilePath, String destDirectory) throws IOException {
            File destDir = new File(destDirectory);
            if (!destDir.exists()) {
                destDir.mkdir();
            }
            ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
            ZipEntry entry = zipIn.getNextEntry();
            // iterates over entries in the zip file
            while (entry != null) {
                String filePath = destDirectory + File.separator + entry.getName();
                if (!entry.isDirectory()) {
                    // if the entry is a file, extracts it
                    extractFile(zipIn, filePath);
                } else {
                    // if the entry is a directory, make the directory
                    File dir = new File(filePath);
                    dir.mkdir();
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
            zipIn.close();
        }

        /**
         * Extracts a zip entry (file entry)
         *
         * @param zipIn
         * @param filePath
         * @throws IOException
         */
        private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
            byte[] bytesIn = new byte[BUFFER_SIZE];
            int read = 0;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
            bos.close();
        }
    }

}
