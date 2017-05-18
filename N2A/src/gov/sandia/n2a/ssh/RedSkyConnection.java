/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ssh;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

public class RedSkyConnection {

    // Inner class for exec results.
    public static class Result {
        public String stdOut = "";
        public String stdErr = "";
        public boolean error = false;
        public int errorCode = 0;

        @Override
        public String toString() {
            return "STDOUT=" + stdOut + "\nSTDERR=" + stdErr + "\nERR=" + error + "\nERRCODE=" + errorCode;
        }
    }

    // Constants
    public static final String RED_SKY_HOST = "redsky";
    public static final int SSH_PORT = 22;

    // Connection state
    private static JSch jsch = new JSch();
    private static Session session = null;

    // This method is used only by the methods in this class and
    // creates an active SSH session with RedSky.
    private synchronized static void connect() throws JSchException {

        // Attempt to connect to RedSky if not already connected.
        if(session == null || !session.isConnected()) {

            // A couple of possible locations for known hosts files.
            File[] knownHostsLocs = new File[] {
                new File(System.getProperty("user.home") + File.separatorChar + ".ssh" + File.separatorChar + "known_hosts"),
                new File(System.getProperty("user.home") + File.separatorChar + "ssh" + File.separatorChar + "known_hosts")
            };

            // See if you can locate the file, and if so, use it.
            for(File loc : knownHostsLocs) {
                if(loc.exists()) {
                    jsch.setKnownHosts(loc.getAbsolutePath());
                    break;
                }
            }

            // Create a new session and attempt to connect.
            String user = System.getProperty("user.name");
            session = jsch.getSession(user, RED_SKY_HOST, SSH_PORT);
            UserInfo ui = new MyUserInfo();
            session.setUserInfo(ui);
            session.connect(30000);
        }
    }

    // Closes the connection to RedSky if it is open.
    // * We may not want to call this even at application
    // shut down because some testing indicates that it
    // may cancel submitted commands.
    public synchronized static void close() throws Exception {
        if(session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    public static Result exec(final String cmd) throws Exception {
        return exec(cmd, false);
    }
    public static Result exec(final String cmd, boolean nonBlocking) throws Exception {

        if(nonBlocking) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        Result r = execRun(cmd);
                        if(r.error) {
                            throw new Exception("Could not execute command. " + r.stdErr);
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();
            return null;
        }

        return execRun(cmd);
    }

    private static Result execRun(String cmd) throws Exception {
        connect();

        Result result = new Result();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        Channel channel = null;

        try {
            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(cmd);
            channel.setInputStream(null);
            ((ChannelExec) channel).setErrStream(errStream);
            InputStream in = channel.getInputStream();
            channel.connect();

            StringBuffer b = new StringBuffer();
            byte[] tmp = new byte[1024];
            while(true) {
                while(in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if(i < 0) {
                        break;
                    }
                    b.append(new String(tmp, 0, i));
                }
                if(channel.isClosed()) {
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch(Exception ee) {}
            }

            result.errorCode = channel.getExitStatus();
            result.error = result.errorCode != 0;
            result.stdOut = b.toString();

        } catch(Exception e) {
            result.error = true;

        } finally {
            if(channel != null) {
                try {
                    channel.disconnect();
                } catch(Exception e) {}
            }
        }

        result.stdErr = errStream.toString();

        return result;
    }

    public static Result send(File srcFile, String destPath) throws Exception {
        connect();

        Result result = new Result();
        FileInputStream fis = null;
        OutputStream out = null;
        Channel channel = null;

        try {
            String lfile = srcFile.getAbsolutePath();

            // exec 'scp -t rfile' remotely
            String command = "scp -p -t '" + destPath + "'";
            channel = session.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);

            // get I/O streams for remote scp
            out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            if(checkAck(in) != 0) {
                result.error = true;
                return result;
            }

            // send "C0644 filesize filename", where filename should not include '/'
            long fileSize = srcFile.length();
            command = "C0644 " + fileSize + " ";
            if(lfile.lastIndexOf('/') > 0) {
                command += lfile.substring(lfile.lastIndexOf('/') + 1);
            } else {
                command += lfile;
            }
            command += "\n";
            out.write(command.getBytes());
            out.flush();
            if(checkAck(in) != 0) {
                result.error = true;
                return result;
            }

            // send a content of lfile
            fis = new FileInputStream(lfile);
            byte[] buf = new byte[1024];
            while(true) {
                int len = fis.read(buf, 0, buf.length);
                if(len <= 0) {
                    break;
                }
                out.write(buf, 0, len); // out.flush();
            }
            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            if(checkAck(in) != 0) {
                result.error = true;
                return result;
            }

        } catch(Exception e) {
            result.error = true;

        } finally {
            if(fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {}
            }
            if(out != null) {
                try {
                    out.close();
                } catch (Exception e) {}
            }
            if(channel != null) {
                try {
                    channel.disconnect();
                } catch (Exception e) {}
            }
        }

        return result;
    }

    public static Result receive(String remote, File dst) throws JSchException {
        connect();

        Result result = new Result();
        FileOutputStream fos = null;
        OutputStream out = null;
        Channel channel = null;

        try {

            // exec 'scp -f rfile' remotely
            String command = "scp -f '" + remote + "'";
            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            // get I/O streams for remote scp
            out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            byte[] buf = new byte[1024];

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            while(true) {
                int c = checkAck(in);
                if(c != 'C') {
                    break;
                }

                // read '0644 '
                in.read(buf, 0, 5);

                long filesize = 0L;
                while(true) {
                    if(in.read(buf, 0, 1) < 0) {
                        // error
                        break;
                    }
                    if(buf[0] == ' ') {
                        break;
                    }
                    filesize = filesize * 10L + (buf[0] - '0');
                }

                String file = null;
                for(int i = 0;; i++) {
                    in.read(buf, i, 1);
                    if(buf[i] == (byte) 0x0a) {
                        file = new String(buf, 0, i);
                        break;
                    }
                }

                // System.out.println("filesize="+filesize+", file="+file);

                // send '\0'
                buf[0] = 0;
                out.write(buf, 0, 1);
                out.flush();

                // read a content of lfile
                File outputFile;
                if(dst.isDirectory()) {
                    outputFile = new File(dst, file);
                } else {
                    outputFile = dst;
                }
                fos = new FileOutputStream(outputFile);
                int foo;
                while(true) {
                    if(buf.length < filesize) {
                        foo = buf.length;
                    } else {
                        foo = (int) filesize;
                    }
                    foo = in.read(buf, 0, foo);
                    if(foo < 0) {
                        // error
                        break;
                    }
                    fos.write(buf, 0, foo);
                    filesize -= foo;
                    if(filesize == 0L) {
                        break;
                    }
                }

                if(checkAck(in) != 0) {
                    System.exit(0);
                }

                // send '\0'
                buf[0] = 0;
                out.write(buf, 0, 1);
                out.flush();
            }

        } catch(Exception e) {
            result.error = true;

        } finally {
            if(fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {}
            }
            if(out != null) {
                try {
                    out.close();
                } catch (Exception e) {}
            }
            if(channel != null) {
                try {
                    channel.disconnect();
                } catch (Exception e) {}
            }
        }

        return result;

    }

    // Helper method for send & receive methods.
    private static int checkAck(InputStream in) throws IOException {
        int b = in.read();
        // b may be 0 for success,
        // 1 for error,
        // 2 for fatal error,
        // -1
        if(b == 0) {
            return b;
        }
        if(b == -1) {
            return b;
        }

        if(b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            } while(c != '\n');
            if(b == 1) { // error
                System.out.print(sb.toString());
            }
            if(b == 2) { // fatal error
                System.out.print(sb.toString());
            }
        }
        return b;
    }
}
