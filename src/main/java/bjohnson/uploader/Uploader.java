package bjohnson.uploader;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.cli.*;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Uploader {
    static UDLHandlerService service;
    static final Logger logger = LoggerFactory.getLogger(Uploader.class);
    public static void main(String[] args) throws ParseException, URISyntaxException, IOException {
        Options options = new Options();
        options.addOption("u", "user", true, "Set UDL Username.")
                .addOption("p", "password", true, "Set UDL Password")
                .addOption("s", "scheme", true, "Set UDL Scheme")
                .addOption("e","endpoint",true,"Set UDL Endpoint for POST")
                .addOption("t","uri",true,"Set complete UDL URI")
                .addOption("h", "host", true,"Set UDL Host")
                .addRequiredOption("f","file",true,"Specify input file to upload")
                .addOption("?","help",false,"Displays usage and help");
        HelpFormatter formatter = new HelpFormatter();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        }catch(MissingOptionException e) {
            formatter.printHelp("java -jar uploader.jar", options,true);
            return;
        }
        String user="";
        String pwd="";
        String scheme="";
        String host="";
        String endpoint="";
        if (cmd.hasOption("?")) {
            formatter.printHelp("java -jar uploader.jar", options,true);
            return;
        }
        if (cmd.hasOption("u")) {
            user = cmd.getOptionValue("u");
        }
        if (cmd.hasOption("p")) {
            pwd = cmd.getOptionValue("p");
        }
        if (cmd.hasOption("s") && cmd.hasOption("h") && cmd.hasOption("e")) {
            scheme=cmd.getOptionValue("s");
            host=cmd.getOptionValue("h");
            endpoint=cmd.getOptionValue("e");
        }else if(cmd.hasOption("t")) {
            URI uri = new URI(cmd.getOptionValue("t"));
            scheme=uri.getScheme();
            host=uri.getHost();
            endpoint=uri.getPath();
        }
        service = new UDLHandlerService(host,scheme,user,pwd);

        //read file and parse datas
        Gson gson = new Gson();
        List<String> jsonDataArray = loadArrayJsonFile(Paths.get(cmd.getOptionValue("f"))).stream().map(gson::toJson).collect(Collectors.toList());
        logger.info("Uploading entries");
        boolean success=true;
        for (String s : jsonDataArray) {
            try {
                Pair<Integer, String> response = service.send("POST", s, endpoint);
                if (response.getFirst() >= 400 || response.getFirst() < 200) {
                    logger.error("Failed to send\n{}\n{}",s,response.getSecond());
                    success=false;
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return;
            } catch (HTTPException e) {
                logger.error(e.getMessage());
            }
        }
        if (success) {
            logger.info("All messages sent successfully");
        }else{
            logger.info("Some data failed to upload");
        }
    }
    public static List<Map<String,Object>> loadArrayJsonFile(Path file) throws IOException {
        Gson gson = new Gson();
        Reader reader = Files.newBufferedReader(file);
        return gson.fromJson(reader, new TypeToken<List<Map<String,Object>>>(){}.getType());
    }
}
