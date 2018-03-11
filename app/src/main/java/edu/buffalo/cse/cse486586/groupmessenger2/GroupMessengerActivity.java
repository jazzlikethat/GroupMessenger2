package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.MessageQueue;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity implements View.OnClickListener {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    String[] REMOTE_PORTS = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    private EditText editText1;
    String myPort;
    int seqNum = 1;
    int writeSequence = 0;
    Queue<Message> messageQueue = new PriorityQueue<Message>();

    private static class Message implements Comparable<Message> {

        private String msg;
        private String ID;
        private double proposal_number;
        private int status; // can be 0, 1 or 2

        /*
        * status = 0 => message
        * status = 1 => proposal
        * status = 2 => agreement
        * */

        public Message(String msg, String ID, double proposal_number, int status) {
            this.msg             = msg;
            this.ID              = ID;
            this.proposal_number = proposal_number;
            this.status          = status;
        }

        @Override
        public int compareTo(Message message) {
            if (this.proposal_number == message.proposal_number){
                Log.d(TAG, "Two messages cannot have same proposal number");
            }

            if (this.proposal_number < message.proposal_number) {
                return -1;
            }
            else if (this.proposal_number == message.proposal_number) {
                return 0;
            }
            else {
                return 1;
            }
        }
    }

    public String getUniqueId(){
        return UUID.randomUUID().toString();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        editText1 = (EditText) findViewById(R.id.editText1);

        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Unable to create a ServerSocket");
            return;
        }
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        findViewById(R.id.button4).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if(view.getId() == R.id.button4)
        {
            String msg = editText1.getText().toString();
            msg = msg + "###" + getUniqueId() + "###" + 0;
            editText1.setText(""); // This is one way to reset the input box.
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Message message, message2, message3;

            while (true) {
                try {
                    Socket socket = serverSocket.accept();

                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),true);

                    String str = in.readLine();
                    String str_split[] = str.split("###");

                    if (Integer.parseInt(str_split[2]) == 0) {

                        // Add the message to the queue
                        int status = 1;
                        double proposal_number = Double.parseDouble(seqNum + "." + myPort);
                        seqNum++;
                        message = new Message(str_split[0], str_split[1], proposal_number, status);
                        messageQueue.add(message);

                        // Send the proposal number back to the queue
                        out.println(Double.toString(proposal_number));
                    }
                    else if (Integer.parseInt(str_split[2]) == 2) {

                        // Remove the message present in the queue
                        for (Message message1 : messageQueue) {
                            if (message1.ID.equals(str_split[1])) {
                                messageQueue.remove(message1);
                                break;
                            }
                        }

                        // Add the new message into the queue with the correct proposal number
                        int status = 2;
                        double proposal_number = Double.parseDouble(str_split[3]);
                        message2 = new Message(str_split[0], str_split[1], proposal_number, status);
                        messageQueue.add(message2);

                        // Get the integer value from the proposal_number
                        // Change the global sequence number if it is less than the above integer
                        int intValue = (int) proposal_number;
                        if (seqNum <= intValue) {
                            seqNum = intValue + 1;
                        }

                        //Publish the head of the queue if its ready
                        while (messageQueue.iterator().hasNext()){
                            message3 = messageQueue.peek();
                            if (message3 == null || message3.status != 2){
                                break;
                            }
                            message3 = messageQueue.poll();
                            publishProgress(message3.msg);
                        }
                    }

                    socket.close();
                }
                catch (IOException e) {
                    Log.e(TAG, "Failed to publish message");
                }
            }

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */

        }

        protected void onProgressUpdate(String...strings) {
            String strReceived = strings[0].trim();
            TextView textView = (TextView) findViewById(R.id.textView1);
            textView.append(strReceived + "\n");

            String filename = Integer.toString(writeSequence);

            Uri uri = new Uri.Builder().authority("edu.buffalo.cse.cse486586.groupmessenger2.provider").scheme("content").build();
            ContentValues cv = new ContentValues();
            cv.put("key", filename);
            cv.put("value", strReceived);
            getContentResolver().insert(uri, cv);

            writeSequence++;

            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            try {

                double proposal_numbers[] = new double[5];
                String msgToSend = msgs[0];

                for (int i = 0; i < REMOTE_PORTS.length; i++) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORTS[i]));

                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.println(msgToSend);
                    String numberAsString = in.readLine();

                    socket.close();

                    double proposal_number = Double.parseDouble(numberAsString);
                    proposal_numbers[i] = proposal_number;
                }

                // Conclude the proposal number
                double max_pn = proposal_numbers[0];
                for (int j = 1; j < proposal_numbers.length; j++) {
                    if (max_pn < proposal_numbers[j]) {
                        max_pn = proposal_numbers[j];
                    }
                }

                // Send the agreement with the proposal number
                String msg_split[] = msgToSend.split("###");
                msg_split[2] = "2";
                String agreedMessage = TextUtils.join("###", msg_split);
                agreedMessage = agreedMessage +  "###" + Double.toString(max_pn);

                for (int j = 0; j < REMOTE_PORTS.length; j++) {
                    Socket socket2 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORTS[j]));

                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket2.getOutputStream())),true);
                    out.println(agreedMessage);
                    socket2.close();
                }
            }
            catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            }
            catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }
}
