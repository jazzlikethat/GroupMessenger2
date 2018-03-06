package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

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

    private int seqNum = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

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
            Log.d(TAG,"Msg to send "+msg);
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
            Log.e(TAG, "in server task");
            ServerSocket serverSocket = sockets[0];
            String msgToSend;

            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    InputStream inputStream = socket.getInputStream();
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    msgToSend = bufferedReader.readLine();

                    publishProgress(msgToSend);

                    bufferedReader.close();
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

            String filename = Integer.toString(seqNum);

            Log.d(TAG,"Server task msg received "+ strReceived);

            Uri uri = new Uri.Builder().authority("edu.buffalo.cse.cse486586.groupmessenger2.provider").scheme("content").build();
            ContentValues cv = new ContentValues();
            cv.put("key", filename);
            cv.put("value", strReceived);
            getContentResolver().insert(uri, cv);
            seqNum++;

            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                for (int i = 0; i < REMOTE_PORTS.length; i++) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORTS[i]));

                    String msgToSend = msgs[0];

                    OutputStream outputStream = socket.getOutputStream();
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
                    bufferedWriter.write(msgToSend);

                    bufferedWriter.flush();
                    bufferedWriter.close();
                    socket.close();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }
}
