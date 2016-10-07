package hh.tools.analytics.httpd;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.junit.Test;

/**
 * Created by hugues.hivert on 01/07/2016.
 */
public class ApplicationTest{


    public void extractUsersInParams() throws IOException {
        Application app = new Application(0, "newsesame_prod.properties");
        String prefixFileName = "BE_CA_TC-newsesame-back-web-";

        Collection<File>
            files =
            FileUtils.listFiles(app.backupDir, FileFilterUtils.prefixFileFilter(prefixFileName),
                FileFilterUtils.directoryFileFilter());

        HashMap<String, String> users = new HashMap<String, String>();

        List<String> nlines = new ArrayList<String>();


        for(File file : files){
            System.out.println(" Find log file  " + file.getAbsolutePath());

            String accesApplicationVide = "false";
            List<String> lines = FileUtils.readLines(file);
            for(String line : lines){
                if(line.contains("IdentifiantAcces du CTXAccesApplication")){
                    accesApplicationVide = "true";
                }
                if(line.contains("Début appel du service [MPERS01")){
                    String[] splitLine = line.split(" - ");
                    //Cle numcr;user issue des log OPEN
                    String crEtUser = splitLine[0].substring(splitLine[0].length() - 5) + ";" + splitLine[1];
                    //Utilisateur connu
                    String userAccesApplication = accesApplicationVide;
                    if("true".equals(userAccesApplication)){
                        //Ce user est connu, Cool
                        if(users.get(crEtUser) != null){
                            if(!users.get(crEtUser).equals(accesApplicationVide)){
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
        for(String userName : users.keySet()){
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
    public void extractFonctionnalEventSimm() throws  Exception{
        Collection<File>
            files =
            FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod"), FileFilterUtils.suffixFileFilter("filter-Flux-difuse-au-poste-distributeur.txt"),
                FileFilterUtils.directoryFileFilter());

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Timestamp;Num CR;User;CorrelationId;Reference Archivage;Reference bancaire;Numero dossier;Numero sous dossier;Code groupe document;Identifiant protocole concentement");

        for(File file : files){
            System.out.println(" Find log file :" + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file);
            for(String line : lines){
                //OUPS c'est le flux SIMM :
                if (line.contains("<donneesLancementSignature><identifiantOperationDemandeur>")){
                    String[] ttab = line.split(" - ");
                    if(ttab.length > 4  ){
                        System.out.println(" WARN log line contain 4 ' - ' ");
                    }
                    StringBuilder csvLine = new StringBuilder();
                    csvLine.append(Application.extractXmlValueFromString(ttab[3],"<dateHeureFinValidite>",29)).append(Application.CSV_SEP);
                    csvLine.append(ttab[0].substring(ttab[0].length() -5 )).append(Application.CSV_SEP);
                    csvLine.append(ttab[1]).append(Application.CSV_SEP);
                    csvLine.append(ttab[2]).append(Application.CSV_SEP);
                    csvLine.append(Application.extractXmlValuesFromString(ttab[3],"<identifiantOperationDemandeur>")).append(Application.CSV_SEP);
                    csvLine.append(Application.extractXmlValuesFromString(ttab[3],"<identifiantPartenaireSignataire>")).append(Application.CSV_SEP);
                    csvLine.append(Application.extractXmlValuesFromString(ttab[3],"<numeroDossier>")).append(Application.CSV_SEP);
                    csvLine.append(Application.extractXmlValuesFromString(ttab[3],"<numeroSousDossier>")).append(Application.CSV_SEP);
                    csvLine.append(Application.extractXmlValuesFromString(ttab[3],"<codeGroupeDocument>")).append(Application.CSV_SEP);
                    csvLine.append(Application.extractXmlValuesFromString(ttab[3],"<identifiantProtocoleConsentement>")).append(Application.CSV_SEP);
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


    /**
     * Anomalie Production bascule SEA vers SIMM
     */

    @Test
    public void extractTechnicalErrorCEM() throws  Exception{

        Collection<File>
            files =
            FileUtils.listFiles(new File("C:\\PTOD\\temp\\logs\\prod\\20161005"), new String[]{"txt"},
                true);

        List<String> nlines = new ArrayList<String>();
        //Ajout du HEADER CSV
        nlines.add("Timestamp;Num CR;User;CorrelationId;");

        for(File file : files){
            System.out.println(" Find log file :" + file.getAbsolutePath());

            String day = file.getName().substring(19,29);

            List<String> lines = FileUtils.readLines(file);
            for(String line : lines){
                //Cette ligne d'erreur n'est pas sufisante, elle ne couvre pas les cas renconrés pour le moment.
                if (line.contains(" : 500")){
                    String[] ttab = line.split(" - ");
                    if(ttab.length > 4  ){
                        System.out.println(" WARN log line contain 4 ' - ' ");
                    }
                    StringBuilder csvLine = new StringBuilder();
                    csvLine.append(day).append("-").append(ttab[0].substring(0,12)).append(Application.CSV_SEP);
                    csvLine.append(ttab[0].substring(ttab[0].length() -5 )).append(Application.CSV_SEP);
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
