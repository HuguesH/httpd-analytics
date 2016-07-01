package hh.tools.analytics.httpd;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

        HashSet<String> users = new HashSet<String>();

        List<String> nlines = new ArrayList<String>();

        String  lineWarn = " - ; - ; ";
        for(File file : files){
            System.out.println(" Find log file  " + file.getAbsolutePath());

            List<String> lines = FileUtils.readLines(file);
            for(String line : lines){
                if(line.contains("IdentifiantAcces du CTXAccesApplication")){
                    lineWarn = line;
                }
                if(line.contains("Début appel du service [MPERS01")){
                    if(lineWarn != " - ; - ; "){
                        nlines.add(lineWarn + app.CSV_SEP + line);
                        users.add(line.split(" - ")[1]);
                        lineWarn = " - ; - ; ";
                    }
                }

            }

        }




        File fSaved = FileUtils.getFile(app.backupDir, prefixFileName + "list.csv");
        FileUtils.writeLines(fSaved, nlines);
        System.out.println(
            "Ecriture du fichier aggrege " + fSaved.getCanonicalPath() + " : " + String.valueOf(nlines.size())
                + " lines ");

        System.out.println("Identification de : "+ String.valueOf(users.size()) );

    }




}
