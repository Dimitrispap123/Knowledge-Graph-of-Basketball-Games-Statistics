import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openrdf.model.URI;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParseException;
import virtuoso.sesame2.driver.VirtuosoRepository;



public class Virtuoso {
    String virtuosoHost = "localhost";
    String virtuosoPort = "1111";
    String virtuosoUserName = "dba";
    String virtuosoPassword = "dba";

    public static void main(String[] args) {
        Virtuoso vir = new Virtuoso();
        // Upload all files from output folder
        vir.uploadAllFiles();
    }
    public void uploadAllFiles() {
        String basePath = "output";
        File outputFolder = new File(basePath);

        if (outputFolder.exists() && outputFolder.isDirectory()) {
            System.out.println("Processing output folder: " + basePath);

            // Finding all .ttl files
            File[] ttlFiles = outputFolder.listFiles((dir, name) -> name.endsWith(".ttl"));

            if (ttlFiles != null && ttlFiles.length > 0) {
                System.out.println("Found " + ttlFiles.length + " TTL files to upload");

                for (File ttlFile : ttlFiles) {
                    uploadNewFile(ttlFile.getAbsolutePath());
                }
            } else {
                System.out.println("No TTL files found in output directory");
            }
        } else {
            System.out.println("Output folder not found: " + basePath);
        }
    }

    public void uploadNewFile(String file) {
        File f = new File(file);
        Virtuoso upload = new Virtuoso();

        String graphspace = "http://www.ics.forth.gr/isl/EuroleagueKG";
        try {
            upload.uploadFileToVirtuoso(f, graphspace);
            System.out.println("Uploaded: " + file);
        } catch (RepositoryException ex) {
            Logger.getLogger(Virtuoso.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Virtuoso.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RDFParseException ex) {
            Logger.getLogger(Virtuoso.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void uploadFileToVirtuoso(File file, String graphSpace) throws RepositoryException, IOException, RDFParseException {

        VirtuosoRepository virt_repository = new VirtuosoRepository("jdbc:virtuoso://"
                + virtuosoHost + ":" + virtuosoPort
                + "/charset=UTF-8/log_enable=2",
                virtuosoUserName, virtuosoPassword);

        RepositoryConnection conn = virt_repository.getConnection();
        System.out.println("Uploading File: " + file + " to graphSpace: " + graphSpace);
        RDFFormat format = RDFFormat.TURTLE;
        URI graph = conn.getRepository().getValueFactory().createURI(graphSpace);
        conn.add(file, null, format, graph);
        conn.close();
    }

}