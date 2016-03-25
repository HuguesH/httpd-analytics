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

/**
 * Created by Hugues on 07/03/2016.
 */
public class Application{


  static final String           CSV_SEP       = ";";

  static final String[]         machines      = new String[]{"vl-c-pxx-33", "vl-c-pxx-34"};

  static final SimpleDateFormat dateFormat    = new SimpleDateFormat("HH:mm:ss.SSS");

  static final SimpleDateFormat dayLogsFormat = new SimpleDateFormat("YYYYMMdd");

  private File                  saslogDir;

  private File                  targetDir;

  private File                  backupDir;

  private String                dayDirName;


  /**
   * LogFormat "%{%Y-%m-%d}t;%{%H:%M:%S}t;%s;%D;%b;%h;%m;%{Host}i;%U;%q;%{JSESSIONID}C;%{Cookie}n"
   * common
   */
  String                        columnName    =
      "Jour;Heure;HttpStatus;Duree;Bytes;IP;Method;Host;URI;QueryParams;serveur;service;cleanURI;HH";


  public static void main(String[] args) {
    try{

      // create Options object
      Options options = new Options();
      options.addOption("h", "help", false, " write help");
      options.addOption("c", "copySasLog", false, "copy sas log to local directory and unzip all files");
      options.addOption("a", "access", false, "aggregate httpd access log and add stats columns");
      options.addOption("t", "tomcat", false, "aggregate tomcat log");
      options.addOption("s", "stats", false, "aggregate httpd stats all days");
      //options.addOption(Option.builder().argName("correlationId").hasArg().desc("Correlation id seq").build());


      // parse the command line arguments
      CommandLineParser parser = new DefaultParser();
      CommandLine line = parser.parse( options, args );

      // validate that block-size has been set
      if( line.hasOption( "h" ) ) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "logs-analytics", options );
      }

      Application application = new Application(0);
      //Deplace et decompress les traces de NewSesame.
      if(line.hasOption("c")) {
        application.copyAndUnzipDayLogs();
      }
      //Aggrege les access log des deux machines et ajoute des colonnes facilitant les stats
      if(line.hasOption("a")) {
        application.aggregateDayAccessLogHttpd();
      }

      //Aggrege toutes les LOG Tomcat dans un fichier trié par date
      if(line.hasOption("t")) {
        application.aggregateDayBackEndLog("] ERROR ");
      }

      //Genere un fichier global pour travailler sur toutes les stats en même temps.
      if(line.hasOption("s")) {
        application.aggregateAllAccessLogHttpd();
      }

    }catch(Exception e){
      System.out.println(" Exception " + e.getMessage());
      e.printStackTrace();
    }


  }

  public Application(final int beforeToday) throws IOException {
    Properties prop = new Properties();
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream("app_home.properties");
    prop.load(inputStream);

    saslogDir = new File(prop.getProperty("saslog.dir"));
    System.out.println("SASLOG  DIR : " + saslogDir);
    targetDir = new File(prop.getProperty("target.dir"));
    System.out.println("TARGET  DIR : " + targetDir);
    backupDir = new File(prop.getProperty("backup.dir"));
    System.out.println("BACKUP  DIR : " + backupDir);

    Calendar cal = Calendar.getInstance();
    cal.add(GregorianCalendar.DAY_OF_MONTH, -beforeToday);
    dayDirName = dayLogsFormat.format(cal.getTime());
    System.out.println(" Work on day : " + dayDirName);

  }

  private void aggregateAllAccessLogHttpd() throws IOException {

    Collection<File> files = FileUtils.listFiles(backupDir, FileFilterUtils.prefixFileFilter("aggrega-access-clean"), FileFilterUtils.directoryFileFilter());

    List<String> nlines = new ArrayList<String>();
    for(File file : files){
      System.out.println(" Find log file  " + file.getAbsolutePath());
      List<String> lines = FileUtils.readLines(file);
      for(String line : lines){
        nlines.add(line);
      }

    }

    File fSaved = FileUtils.getFile(backupDir,  "aggrega-access-all.csv");
    FileUtils.writeLines(fSaved, nlines);
    System.out.println(
        "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size()) + " lines ");
  }




  private void copyAndUnzipDayLogs() throws IOException {


   ZipUtils unzip = new ZipUtils();
    File sasLogDirDay = FileUtils.getFile(saslogDir, dayDirName);

    for(String machine : machines){
      File machioneDir = FileUtils.getFile(sasLogDirDay, machine);
      System.out.println(" Read logs on " + machioneDir.getAbsolutePath());
      Collection<File> files = FileUtils.listFiles(machioneDir, FileFilterUtils.suffixFileFilter(".zip"),
          FileFilterUtils.directoryFileFilter());

      for(File file : files){
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
    }


  }


  private void aggregateDayAccessLogHttpd() throws IOException {

    File dayWorkDirectory = FileUtils.getFile(targetDir, dayDirName);

    Collection<File> files = FileUtils.listFiles(dayWorkDirectory, FileFilterUtils.prefixFileFilter("access"),
        FileFilterUtils.directoryFileFilter());

    List<String> nlines = new ArrayList<String>();
    nlines.add(columnName.toString().replace(',', ';'));

    for(File file : files){
      System.out.println(" Find access file  " + file.getAbsolutePath());

      List<String> lines = FileUtils.readLines(file);
      for(String line : lines){

        if(!line.contains("/health-checks")) {

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
          strBuild.append(cLine[1].substring(0,2)).append(CSV_SEP);

          nlines.add(strBuild.toString());
        }
      }

    }

    File fSaved = FileUtils.getFile(backupDir,  "aggrega-access-clean-" + dayDirName + ".csv");
    FileUtils.writeLines(fSaved, nlines);
    System.out.println(
        "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size()) + " lines ");

  }

  private void aggregateDayBackEndLog(String correlationId) throws IOException {

    final File dayWorkDirectory = FileUtils.getFile(targetDir, dayDirName);

    Collection<File> files = FileUtils.listFiles(dayWorkDirectory,
        FileFilterUtils.prefixFileFilter("newsesame-back-web"), FileFilterUtils.directoryFileFilter());

    List<LineLog> nlines = new ArrayList<LineLog>();


    for(File file : files){
      System.out.println(" Find newsesame-back-web log file  " + file.getAbsolutePath());
      List<String> lines = FileUtils.readLines(file);
      Date time = null;
      boolean goodCorrelationId = true;
      if(StringUtils.isNotEmpty(correlationId)){
        goodCorrelationId = false;
      }
      for(String line : lines){
        boolean noArchive =
            line.contains("INFO  pacifica.ns.web.filter.LoggingFilter") || line.contains("INFO  p.monitoring.") || line.contains("ListeFamille Node is empty") || line.contains("Dossier Node is empty");

        Date dateLine = null;
        if(!noArchive && line.length() > 15){
          line = line.replaceAll(";", "!");
          try{
            String timeLine = line.substring(0, 13);
            if(!StringUtils.isBlank(timeLine)){
              dateLine = dateFormat.parse(timeLine);
              if(dateLine != null){
                time = dateLine;
                if(StringUtils.isNotEmpty(correlationId)){
                  if(line.contains(correlationId)){
                    goodCorrelationId = true;
                  }else {
                    goodCorrelationId = false;
                  }
                }
              }
            }else{
              dateLine = time;
            }
          }catch(ParseException e){
            dateLine = time;
          }

          if(goodCorrelationId){
            nlines.add(new LineLog(dateLine, line, file.getParentFile().getName()));
          }
        }


      }
    }
    Collections.sort(nlines);

    String fileName = "newsesame-back-web-" + dayDirName + ".csv";

    if(StringUtils.isNotEmpty(correlationId)){
      fileName = fileName.replaceFirst(".csv", "-"+correlationId.replaceAll(" ", "-")+".csv");
    }

    File fSaved = FileUtils.getFile(targetDir,  fileName);
    FileUtils.writeLines(fSaved, nlines);
    System.out.println(
        "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size()) + " lines ");

  }



  /**
   * Nettoyage de la chaine de caactere URI pour arriver à une chaine uniqu sans cas fonctionnel.
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
    if(cleanUri.contains("/referentiel/marques")){
      cleanUri = cleanUri.replaceAll("[/][A-Z]{2}[/]", "/{CODE}/");
      cleanUri = cleanUri.replaceAll("[/]\\d{2}[/]", "/{CODE}/");
    }


    return cleanUri;


  }

  private String cleanUriAfterSequence(String uri, String seq) {
    String cleanUri = uri;
    int indexSeq = uri.lastIndexOf(seq);
    if(indexSeq > 0){
      cleanUri = uri.substring(0, indexSeq + seq.length()) + "#";
    }
    return cleanUri;
  }

  private class LineLog implements Comparable<LineLog>{

    LineLog(Date dateTime, String line, String folder) {

      _dateTime = dateTime;
      _line = line;
      _folder = folder;

    }

    Date   _dateTime;
    String _line;
    String _folder;

    public String toString() {
      return _line + ";" + dateFormat.format(_dateTime) + ";" + _folder;
    }

    public int compareTo(LineLog o) {
      return _dateTime.compareTo(o._dateTime);
    }
  }



}
