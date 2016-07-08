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

    @Test public void extractUsersInParams() throws IOException {
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


}
