package hh.tools.analytics.httpd;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;


/**
 * Created by hugues.hivert on 01/07/2016.
 */
public class ApplicationTest {


    public void extractUsersInParams() throws IOException {
        Application app = new Application(0, "newsesame_prod.properties");
        String prefixFileName = "newsesame_prod.properties-BE_";

        Collection<File>
                files =
                FileUtils.listFiles(app.backupDir, FileFilterUtils.prefixFileFilter(prefixFileName),
                        FileFilterUtils.directoryFileFilter());

        HashMap<String, String> users = new HashMap<String, String>();

        List<String> nlines = new ArrayList<String>();


        for (File file : files) {
            System.out.println(" Find log file  " + file.getAbsolutePath());

            String accesApplicationVide = "false";
            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                if (line.contains("IdentifiantAcces du CTXAccesApplication")) {
                    accesApplicationVide = "true";
                }
                if (line.contains("Début appel du service [MPERS01")) {
                    String[] splitLine = line.split(" - ");
                    //Cle numcr;user issue des log OPEN
                    String crEtUser = splitLine[0].substring(splitLine[0].length() - 5) + ";" + splitLine[1];
                    //Utilisateur connu
                    String userAccesApplication = accesApplicationVide;
                    if ("true".equals(userAccesApplication)) {
                        //Ce user est connu, Cool
                        if (users.get(crEtUser) != null) {
                            if (!users.get(crEtUser).equals(accesApplicationVide)) {
                                System.out.println(" !!  Bizarre 2 comportements différents pour le User " + crEtUser);
                                userAccesApplication = "mixte";
                            }
                        }
                    }
                    users.put(crEtUser, userAccesApplication);
                    //remise à blanc de la valeur par defaut
                    accesApplicationVide = "false";
                }
            }
        }


        nlines.add("numCr;user;CTXAccesApplication");
        for (String userName : users.keySet()) {
            nlines.add(userName + ";" + users.get(userName));
        }


        File fSaved = FileUtils.getFile(app.backupDir, prefixFileName + "list-users.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                        + " lines ");

        System.out.println("Identification d'un total de users  :  " + String.valueOf(users.size()));

    }

    /**
     * Anomalie Production bascule SEA vers SIMM
     */
    @Test
    public void extractStatsMartineEvent() throws Exception {


        Map<String, String> mUsersProfile = new HashMap<String, String>();
        List<StatsLcl> lStats = new ArrayList<StatsLcl>();
        lStats.add(StatsLcl.getStatEntete());

        extratStatsMartineNNIEvents(mUsersProfile, lStats);

        extractFonctionnalMartineEvent("creation-devis", mUsersProfile, lStats);
        extractFonctionnalMartineEvent("creation-propositions", mUsersProfile, lStats);
        extractFonctionnalMartineEvent("creation-contrats", mUsersProfile, lStats);
        extractFonctionnalMartineEvent("modification-contrats", mUsersProfile, lStats);


        File fSaved = FileUtils.getFile("C:\\PTOD\\temp\\logs\\prod", "STATS-LCL-NS-production.csv");
        FileUtils.writeLines(fSaved, lStats);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(lStats.size())
                        + " lines ");
    }

    private void extratStatsMartineNNIEvents(Map<String, String> mUsersProfile, List<StatsLcl> lStats) throws IOException {
        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod"), FileFilterUtils.suffixFileFilter("NNI-lcl.txt"),
                        FileFilterUtils.directoryFileFilter());
        for (File file : files) {
            System.out.println(" Find log file :" + file.getAbsolutePath());
            List<String> lines = FileUtils.readLines(file);
            String beforeLine = "";
            for (String line : lines) {
                if (line.startsWith("<ns2:CONTEXT")) {
                    StatsLcl statsLcl = new StatsLcl();
                    statsLcl.profile = Application.extractXmlAttributeValueFrom(line, "userProfile", 3);
                    statsLcl.user = Application.extractXmlAttributeValueFrom(line, "userId", 6);
                    statsLcl.correlationId = Application.extractXmlAttributeValueFrom(line, "correlationId", 77).substring(41);
                    statsLcl.numcr = Application.extractXmlAttributeValueFrom(line, "NUMCRT", 5);
                    statsLcl.action = " lancement NNI";
                    statsLcl.log = "/lcl : 200";
                    if (beforeLine.length() > 155) {
                        statsLcl.timestamp = beforeLine.substring(0, 24);
                        String[] ttab = beforeLine.split(" - ");
                        if (ttab.length > 4) {
                            System.out.println(" WARN log line contain 4 ' - ' ");
                        }
                        statsLcl.correlationId = ttab[2];
                        if (!statsLcl.user.equalsIgnoreCase(ttab[1])) {
                            System.out.println(" WARN log before is bad User ");
                        }

                    }
                    lStats.add(statsLcl);
                    mUsersProfile.put(statsLcl.user, statsLcl.profile);
                } else {
                    beforeLine = line;
                }
            }
        }
    }

    public void extractFonctionnalMartineEvent(String event, Map<String, String> mUsersProfile, List<StatsLcl> lStats) throws Exception {
        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod"), FileFilterUtils.suffixFileFilter(event + ".txt"),
                        FileFilterUtils.directoryFileFilter());


        for (File file : files) {
            System.out.println(" Find log file :" + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                StatsLcl statsLcl = new StatsLcl();
                statsLcl.timestamp = line.substring(0, 24);
                String[] ttab = line.split(" - ");
                if (ttab.length > 4) {
                    System.out.println(" WARN log line contain 4 ' - ' ");
                }
                statsLcl.numcr = ttab[0].substring(ttab[0].length() - 5);
                statsLcl.user = ttab[1];
                statsLcl.correlationId = ttab[2];
                if (mUsersProfile.get(statsLcl.user) != null) {
                    statsLcl.profile = mUsersProfile.get(statsLcl.user);
                    statsLcl.action = event;
                    statsLcl.log = ttab[3].split("\\|")[0].replaceFirst("\\d{10,40}", "#####");
                    lStats.add(statsLcl);
                } else {
                    if (statsLcl.numcr.equalsIgnoreCase("20000")) {
                        System.out.println(" WARN log line Not Found User ");
                    }
                }
            }
        }
    }


    /**
     * Anomalie Production bascule SEA vers SIMM
     */
    @Test
    public void extractContratMamanEvent() throws Exception {
        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod"), FileFilterUtils.suffixFileFilter("contrat--201.txt"),
                        FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Timestamp;Heure;Reseau Distrb.;Num CR;User;CorrelationId;produit");

        for (File file : files) {
            System.out.println(" Find log file :" + file.getAbsolutePath());


            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                //OUPS c'est le flux SIMM :
                if (line.contains(": 201 |")) {
                    String[] ttab = line.split(" - ");
                    if (ttab.length > 4) {
                        System.out.println(" WARN log line contain 4 ' - ' ");
                    }
                    StringBuilder csvLine = new StringBuilder();
                    csvLine.append(ttab[0].substring(0, 10)).append(Application.CSV_SEP);
                    csvLine.append(ttab[0].substring(11, 17)).append(Application.CSV_SEP);
                    String numCr = ttab[0].substring(ttab[0].length() - 5);
                    csvLine.append(numCr.equals("20000") ? "LCL" : "CRCA").append(Application.CSV_SEP);
                    csvLine.append(numCr).append(Application.CSV_SEP);
                    csvLine.append(ttab[1]).append(Application.CSV_SEP);
                    csvLine.append(ttab[2]).append(Application.CSV_SEP);
                    csvLine.append(ttab[3].substring(10, 12));
                    nlines.add(csvLine.toString());

                }
            }
        }
        File fSaved = FileUtils.getFile("C:\\PTOD\\temp\\logs\\prod", "STATS-Contrats-NS-production.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                        + " lines ");
    }

    @Test
    public void extractFonctionnalEventSEAll() throws IOException {

        Application application = new Application(1, "newsesame_prod.properties");
        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod"), FileFilterUtils.suffixFileFilter("filter-Flux-difuse-au-poste-distributeur.txt"),
                        FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Day;Time;Reseau; Num CR;User;CorrelationId;Reference Archivage;Reference bancaire;Numero dossier;Numero sous dossier;Produit;Maquette;URL");

        for (File file : files) {
            System.out.println(" Find log file :" + file.getAbsolutePath());


            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                String[] ttab = line.split(" - ");
                if (ttab.length > 4) {
                    System.out.println(" WARN log line contain 4 ' - ' ");
                }
                StringBuilder csvLine = new StringBuilder();
                csvLine.append(ttab[0].substring(0, 10)).append(Application.CSV_SEP);
                csvLine.append(" ").append(ttab[0].substring(11, 24)).append(Application.CSV_SEP);
                String numCr = ttab[0].substring(ttab[0].length() - 5);
                csvLine.append(numCr.equals("20000") ? "LCL" : "CRCA").append(Application.CSV_SEP);
                csvLine.append(numCr).append(Application.CSV_SEP);
                csvLine.append(ttab[1]).append(Application.CSV_SEP);
                csvLine.append(ttab[2]).append(Application.CSV_SEP);

                //SEA LCL
                if (line.contains("\"URL_PAPIER\":\"/StockageEditique_LCL/EDITIQUE/")) {
                    exportSEAMessage(ttab[3], csvLine);
                }
                // SIMM
                if (line.contains("<donneesLancementSignature><identifiantOperationDemandeur>")) {
                    exportSimmElement(ttab[3], csvLine);
                }

                nlines.add(csvLine.toString());
            }
        }
        File fSaved = FileUtils.getFile("C:\\PTOD\\temp\\logs\\prod", "STATS-Contrats-SEA-production.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                        + " lines ");


    }

    /**
     *
     */
    private void exportSEAMessage(String message, StringBuilder csvLine) throws IOException {
        JsonFactory jfactory = new JsonFactory();
        int startJson = message.indexOf("{");
        int endJson = message.lastIndexOf("}");

        JsonParser jParser = jfactory.createParser(message.substring(startJson, endJson));

        String refSEA = null;
        String idpart = null;
        String numeroDossier = null;
        String numeroSousDossier = null;
        String codeProduit = null;
        String maquettePapier = null;
        String URL = null;
        boolean paper = false;
        while (!jParser.isClosed()) {
            JsonToken jsonToken = jParser.nextToken();
            if (JsonToken.FIELD_NAME.equals(jsonToken)) {
                String fieldName = jParser.getCurrentName();
                //Passage a l'element suivant.
                jsonToken = jParser.nextToken();
                if ("REF_DEMANDE_SEA".equalsIgnoreCase(fieldName)) {
                    refSEA = jParser.getValueAsString();
                } else if ("IDPART".equals(fieldName)) {
                    idpart = jParser.getValueAsString();
                } else if ("NODOSS".equals(fieldName)) {
                    numeroDossier = jParser.getValueAsString();
                } else if ("NOSSDOSS".equalsIgnoreCase(fieldName)) {
                    numeroSousDossier = jParser.getValueAsString();
                } else if ("IDPTGPDT".equalsIgnoreCase(fieldName)) {
                    codeProduit = jParser.getValueAsString();
                } else if ("LISTE_DOC_PAPIER".equalsIgnoreCase(fieldName)) {
                    paper = true;
                }
                if (paper) {
                    if ("ID_MQT".equalsIgnoreCase(fieldName)) {
                        maquettePapier = jParser.getValueAsString();
                    } else if ("URL_PAPIER".equalsIgnoreCase(fieldName)) {
                        URL = jParser.getValueAsString();
                        break;
                    }
                }
            }
        }
        csvLine.append(refSEA).append(Application.CSV_SEP);
        csvLine.append(idpart).append(Application.CSV_SEP);
        csvLine.append(numeroDossier).append(Application.CSV_SEP);
        csvLine.append(numeroSousDossier).append(Application.CSV_SEP);
        csvLine.append(codeProduit).append(Application.CSV_SEP);
        csvLine.append(maquettePapier).append(Application.CSV_SEP);
        csvLine.append(URL).append(Application.CSV_SEP);


    }


    /**
     * Export SIMM elements from XML to csv line
     *
     * @param xml
     * @param csvLine
     */
    private void exportSimmElement(String xml, StringBuilder csvLine) {
        csvLine.append(Application.extractXmlValuesFromString(xml, "<identifiantOperationDemandeur>")).append(Application.CSV_SEP);
        csvLine.append(Application.extractXmlValuesFromString(xml, "<identifiantPartenaireSignataire>")).append(Application.CSV_SEP);
        csvLine.append(Application.extractXmlValuesFromString(xml, "<numeroDossier>")).append(Application.CSV_SEP);
        csvLine.append(Application.extractXmlValuesFromString(xml, "<numeroSousDossier>")).append(Application.CSV_SEP);
    }

    /**
     * Statistiques production SEA
     */
    @Test
    public void extractFonctionnalEventSEA() throws Exception {
        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod"), FileFilterUtils.suffixFileFilter("filter-Flux-difuse-au-poste-distributeur.txt"),
                        FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Timestamp;Num CR;User;CorrelationId;Reference Archivage;Reference bancaire;Numero dossier;Numero sous dossier;Code groupe document;Identifiant protocole concentement");

        for (File file : files) {
            System.out.println(" Find log file :" + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                //OUPS c'est le flux SIMM :
                if (line.contains("<donneesLancementSignature><identifiantOperationDemandeur>")) {
                    String[] ttab = line.split(" - ");
                    if (ttab.length > 4) {
                        System.out.println(" WARN log line contain 4 ' - ' ");
                    }
                    StringBuilder csvLine = new StringBuilder();
                    csvLine.append(Application.extractXmlValueFromString(ttab[3], "<dateHeureFinValidite>", 29)).append(Application.CSV_SEP);
                    csvLine.append(ttab[0].substring(ttab[0].length() - 5)).append(Application.CSV_SEP);
                    csvLine.append(ttab[1]).append(Application.CSV_SEP);
                    csvLine.append(ttab[2]).append(Application.CSV_SEP);
                    //TODO lecture du flux adapte
                    nlines.add(csvLine.toString());

                }
            }
        }
        File fSaved = FileUtils.getFile("C:\\PTOD\\temp\\logs\\prod", "flux-SEA-production.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                        + " lines ");
    }

    /**
     * Anomalie Production bascule SEA vers SIMM
     */
    public void extractFonctionnalEventSimm() throws Exception {
        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod"), FileFilterUtils.suffixFileFilter("filter-Flux-difuse-au-poste-distributeur.txt"),
                        FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Timestamp;Num CR;User;CorrelationId;Reference Archivage;Reference bancaire;Numero dossier;Numero sous dossier;Code groupe document;Identifiant protocole concentement");

        for (File file : files) {
            System.out.println(" Find log file :" + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                //OUPS c'est le flux SIMM :
                if (line.contains("<donneesLancementSignature><identifiantOperationDemandeur>")) {
                    String[] ttab = line.split(" - ");
                    if (ttab.length > 4) {
                        System.out.println(" WARN log line contain 4 ' - ' ");
                    }
                    StringBuilder csvLine = new StringBuilder();
                    csvLine.append(Application.extractXmlValueFromString(ttab[3], "<dateHeureFinValidite>", 29)).append(Application.CSV_SEP);
                    csvLine.append(ttab[0].substring(ttab[0].length() - 5)).append(Application.CSV_SEP);
                    csvLine.append(ttab[1]).append(Application.CSV_SEP);
                    csvLine.append(ttab[2]).append(Application.CSV_SEP);
                    exportSimmElement(ttab[3], csvLine);
                    nlines.add(csvLine.toString());

                }
            }
        }
        File fSaved = FileUtils.getFile("C:\\PTOD\\temp\\logs\\prod", "flux-SIMM-production.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                        + " lines ");
    }

    @Test
    public void extractTranscoInseeFonction() throws IOException {

        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod"), FileFilterUtils.suffixFileFilter("filter-TRANSCO-INSEE.txt"),
                        FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Date;Heure;Reseau Distrb.;Num CR;User;CorrelationId;Fonction");

        for (File file : files) {
            System.out.println(" Find log file :" + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file, "UTF-8");
            for (String line : lines) {
                //OUPS c'est le flux SIMM :

                String[] ttab = line.split(" - ");
                if (ttab.length > 4) {
                    System.out.println(" WARN log line contain 4 ' - ' ");
                }
                StringBuilder csvLine = new StringBuilder();
                csvLine.append(ttab[0].substring(0, 10)).append(Application.CSV_SEP);
                csvLine.append(ttab[0].substring(11, 17)).append(Application.CSV_SEP);
                String numCr = ttab[0].substring(ttab[0].length() - 5);
                csvLine.append(numCr.equals("20000") ? "LCL" : "CRCA").append(Application.CSV_SEP);
                csvLine.append(numCr).append(Application.CSV_SEP);
                csvLine.append(ttab[1]).append(Application.CSV_SEP);
                csvLine.append(ttab[2]).append(Application.CSV_SEP);
                String message = ttab[3].split("\\(")[0];
                csvLine.append(message);
                nlines.add(csvLine.toString());


            }
        }
        File fSaved = FileUtils.getFile("C:\\PTOD\\temp\\logs\\prod", "STATS-TRANSCO-INSEE-NS-production.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                        + " lines ");
    }

    @Test
    public void extractErreursInseeFonction() throws IOException {

        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod"), FileFilterUtils.suffixFileFilter("filter-WARN-INSEE.txt"),
                        FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Date;Heure;Reseau Distrb.;Num CR;User;CorrelationId;Fonction");

        for (File file : files) {
            System.out.println(" Find log file :" + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file, "UTF-8");
            for (String line : lines) {
                //OUPS c'est le flux SIMM :

                String[] ttab = line.split(" - ");
                if (ttab.length > 4) {
                    System.out.println(" WARN log line contain 4 ' - ' ");
                }
                StringBuilder csvLine = new StringBuilder();
                csvLine.append(ttab[0].substring(0, 10)).append(Application.CSV_SEP);
                csvLine.append(ttab[0].substring(11, 17)).append(Application.CSV_SEP);
                String numCr = ttab[0].substring(ttab[0].length() - 5);
                csvLine.append(numCr.equals("20000") ? "LCL" : "CRCA").append(Application.CSV_SEP);
                csvLine.append(numCr).append(Application.CSV_SEP);
                csvLine.append(ttab[1]).append(Application.CSV_SEP);
                csvLine.append(ttab[2]).append(Application.CSV_SEP);
                String message = ttab[3];
                csvLine.append(message);
                nlines.add(csvLine.toString());


            }
        }
        File fSaved = FileUtils.getFile("C:\\PTOD\\temp\\logs\\prod", "STATS-WARN-INSEE-NS-production.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                        + " lines ");
    }

    @Test
    public void extractAiguilleurFonction() throws IOException {

        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod"), FileFilterUtils.suffixFileFilter("filter-AIGUILLAGE-#-.txt"),
                        FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Date;Heure;Reseau Distrb.;Num CR;User;CorrelationId;AiguillageRule");

        for (File file : files) {
            System.out.println(" Find log file :" + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file, "UTF-8");
            for (String line : lines) {
                //OUPS c'est le flux SIMM :

                String[] ttab = line.split(" - ");
                if (ttab.length > 4) {
                    System.out.println(" WARN log line contain 4 ' - ' ");
                }
                StringBuilder csvLine = new StringBuilder();
                csvLine.append(ttab[0].substring(0, 10)).append(Application.CSV_SEP);
                csvLine.append(ttab[0].substring(11, 17)).append(Application.CSV_SEP);
                String numCr = ttab[0].substring(ttab[0].length() - 5);
                csvLine.append(numCr.equals("20000") ? "LCL" : "CRCA").append(Application.CSV_SEP);
                csvLine.append(numCr).append(Application.CSV_SEP);
                csvLine.append(ttab[1]).append(Application.CSV_SEP);
                csvLine.append(ttab[2]).append(Application.CSV_SEP);
                csvLine.append(ttab[3].substring(0, 74).replaceFirst("Non géré par new sesame,", "Lancement SesameWeb :"));
                nlines.add(csvLine.toString());


            }
        }
        File fSaved = FileUtils.getFile("C:\\PTOD\\temp\\logs\\prod", "STATS-Aiguillage-NS-production.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                        + " lines ");
    }

    @Test
    public void aggregateAndAnalyseADSUSWeb() throws IOException {
        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod\\20161103"), FileFilterUtils.prefixFileFilter("fwkSuivi_pres.txt"),
                        FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Timestamp;Application Server;Num CR;User;SessionSag;IDPART;VNC");


        for (File file : files) {

            if (file.getName().length() == 23) {
                // Cool fichier de log par caisse
                System.out.println(" Find log file :" + file.getAbsolutePath());
                String numcr = file.getName().substring(18, 23);
                String appServer = file.getParentFile().getName().replaceAll("\\d", "#");

                StringBuilder csvLine = null;
                StringBuilder strCtxB = null;
                StringBuilder strParamsB = null;
                List<String> lines = FileUtils.readLines(file);
                for (String line : lines) {

                    if (line.contains(";CONTEXTE r??u  :")) {
                        //Timestamp
                        csvLine = new StringBuilder(line.substring(0, 19).replaceAll(Application.CSV_SEP, "T").replaceAll("/", "-"));

                    }

                    if (line.contains("<VUENATIONALECONTEXTE>")) {
                        strCtxB = new StringBuilder();
                    }

                    // Entre ouverture  et fermeture du flux XML
                    if (strCtxB != null) {
                        strCtxB.append(line.replaceAll(Application.CSV_SEP, " ").replaceAll("  ", " "));
                        //Fin de contexte,
                        if (line.contains("</VUENATIONALECONTEXTE>")) {
                            //On doit pouvoir extraire des attributs XML :
                            String strCtx = strCtxB.toString();
                            strCtxB = null;

                            //AS et NUMCR
                            csvLine.append(Application.CSV_SEP).append(appServer).append(Application.CSV_SEP).append(numcr);
                            csvLine.append(Application.CSV_SEP).append(Application.extractXmlAttributeValueFrom(strCtx, "identifiantAcces", 7));
                            csvLine.append(Application.CSV_SEP).append(Application.extractXmlAttributeValueFrom(strCtx, "idSessionSAG", 33));
                            csvLine.append(Application.CSV_SEP).append(Application.extractXmlAttributeValueFrom(strCtx, "IDPART", 14));


                            //Finallement on ajoute le XML :
                            csvLine.append(Application.CSV_SEP).append(strCtx);
                            nlines.add(csvLine.toString());
                            csvLine = null;
                        }
                    }
                }

            }

        }
        File fSaved = FileUtils.getFile("C:\\PTOD\\temp\\logs\\prod", "STATS-SesameWeb-production.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                        + " lines ");

    }


    /**
     * Anomalie Production bascule SEA vers SIMM
     */

    @Test
    public void extractTechnicalErrorCEM() throws Exception {

        Collection<File>
                files =
                FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod\\20161005"), new String[]{"txt"},
                        true);

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Timestamp;Num CR;User;CorrelationId;");

        for (File file : files) {
            System.out.println(" Find log file :" + file.getAbsolutePath());

            String day = file.getName().substring(19, 29);

            List<String> lines = FileUtils.readLines(file);
            for (String line : lines) {
                //Cette ligne d'erreur n'est pas sufisante, elle ne couvre pas les cas renconrés pour le moment.
                if (line.contains(" : 500")) {
                    String[] ttab = line.split(" - ");
                    if (ttab.length > 4) {
                        System.out.println(" WARN log line contain 4 ' - ' ");
                    }
                    StringBuilder csvLine = new StringBuilder();
                    csvLine.append(day).append("-").append(ttab[0].substring(0, 12)).append(Application.CSV_SEP);
                    csvLine.append(ttab[0].substring(ttab[0].length() - 5)).append(Application.CSV_SEP);
                    csvLine.append(ttab[1]).append(Application.CSV_SEP);
                    csvLine.append(ttab[2]).append(Application.CSV_SEP);
                    nlines.add(csvLine.toString());

                }
            }
        }
        File fSaved = FileUtils.getFile("C:\\PTOD\\temp\\logs\\prod", "500-production-2016-09-29.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
                "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                        + " lines ");
    }
}
