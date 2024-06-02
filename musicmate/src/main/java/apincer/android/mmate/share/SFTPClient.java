package apincer.android.mmate.share;

import static com.jcraft.jsch.ChannelSftp.OVERWRITE;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.util.Properties;

public class SFTPClient {
    private final JSch jsch;
    private ChannelSftp channel;
    private Session session;

    public SFTPClient() {
        jsch = new JSch();
    }

    public boolean connect(String host, int port, String username, String password) throws JSchException {
        session = jsch.getSession(username, host, port);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.setPassword(password);
        session.connect();
        channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        return true;
    }

    /**
     * Upload a file to remote
     *
     * @param localPath  full path of location file
     * @param remotePath full path of remote file
     * @throws JSchException If there is any problem with connection
     * @throws SftpException If there is any problem with uploading file permissions etc
     */
    public void uploadFile(String localPath, String remotePath, String remoteFilename) throws JSchException, SftpException {
        System.out.printf("Uploading [%s] to [%s]...%n", localPath, remotePath);
        if (channel == null) {
            throw new IllegalArgumentException("Connection is not available");
        }
        channel.put(localPath, remotePath+"/"+remoteFilename,null, OVERWRITE);
    }

    /**
     * Download a file from remote
     *
     * @param remotePath full path of remote file
     * @param localPath  full path of where to save file locally
     * @throws SftpException If there is any problem with downloading file related permissions etc
     */
    public void downloadFile(String remotePath, String localPath) throws SftpException {
        System.out.printf("Downloading [%s] to [%s]...%n", remotePath, localPath);
        if (channel == null) {
            throw new IllegalArgumentException("Connection is not available");
        }
        channel.get(remotePath, localPath);
    }

    void mkdirs(String path) throws SftpException {
        ChannelSftp sftp = channel;
        String cwd = sftp.pwd();

       // while (!path.startsWith(cwd)) {
            // System.out.println(cwd + " " + path);
       //     sftp.cd(".."); // should throw exception if can't cdup
            // System.out.println("CDUP!");
       //     cwd = sftp.pwd();
       // }
        sftp.cd("/");
        cwd = sftp.pwd();

        String mkPath = path.substring(cwd.length(), path.length());
        // System.out.println("DIRS TO MAKE: " + mkPath);

        // String dirs[] = splitPath(mkPath);
        String[] dirs = mkPath.split("/");

        for (int i = 0; i < dirs.length; i++) {
            //  System.out.println("mkdir " + dirs[i]);
            //logger.info("mkdir " + dirs[i]);
            // swallow exception that results from trying to
            // make a dir that already exists
            try {
                sftp.mkdir(dirs[i]);
            } catch (Exception ex) {
            }

            // change to the new dir
            // throws an exception if something went wrong
            sftp.cd(dirs[i]);
            cwd = sftp.pwd();
        }
    }

    public void exec(String command) throws JSchException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.connect();
        channel.disconnect();
    }
}