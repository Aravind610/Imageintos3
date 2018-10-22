package com.nnk.imageintos3;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.util.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final String KEY = "xxx";
    private final String SECRET = "xxxxxx";

    private AmazonS3Client s3Client;
    private BasicAWSCredentials credentials;
    private static final int CHOOSING_IMAGE_REQUEST = 1234;

    private EditText edtFileName;
    private TextView textView;
    ImageView imageView;

    private Uri fileUri;
    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.iv1);
        textView = findViewById(R.id.tv1);
        edtFileName = findViewById(R.id.et1);

        findViewById(R.id.iv1).setOnClickListener(this);
        findViewById(R.id.b1).setOnClickListener(this);
        findViewById(R.id.b2).setOnClickListener(this);

        AWSMobileClient.getInstance().initialize(this).execute();

        credentials = new BasicAWSCredentials(KEY, SECRET);
        s3Client = new AmazonS3Client(credentials);
    }

    public void showChoosingFile() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Image"), CHOOSING_IMAGE_REQUEST);
    }

    public void onActivityResult(int req_code, int res_code, Intent data) {
        super.onActivityResult(req_code, res_code, data);
        if (bitmap != null) {
            bitmap.recycle();
        }

        if (req_code == CHOOSING_IMAGE_REQUEST && res_code == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), fileUri);
            } catch (IOException e) {
                e.printStackTrace();
               // imageView.setImageBitmap(bitmap);
            }
        }
    }

    public void uploadFile() {
        if (fileUri != null) {
            final String fileName = edtFileName.getText().toString();

            if (validateInputFileName(fileName)) {
                return;
            }

            final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "/" + fileName);

            createFile(this, fileUri, file);

            TransferUtility transferUtility =
                    TransferUtility.builder()
                            .context(this)
                            .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                            .s3Client(s3Client)
                            .build();

            TransferObserver uploadObserver =
                    transferUtility.upload("aravind_first/" + fileName + "." + getFileExtension(fileUri), file);

            uploadObserver.setTransferListener(new TransferListener() {

                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (TransferState.COMPLETED == state) {
                        Toast.makeText(MainActivity.this, "Upload Completed!", Toast.LENGTH_SHORT).show();
                        file.delete();
                    } else if (TransferState.FAILED == state) {
                        file.delete();
                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                    int percentDone = (int) percentDonef;

                    textView.setText("ID:" + id + "|bytesCurrent: " + bytesCurrent + "|bytesTotal: " + bytesTotal + "|" + percentDone + "%");
                }

                @Override
                public void onError(int id, Exception ex) {
                    ex.printStackTrace();
                }

            });
        }
    }
    private void downloadFile() {
        if (fileUri != null) {

            final String fileName = edtFileName.getText().toString();

            if (validateInputFileName(fileName)) {
                return;
            }

            try {
                final File localFile = File.createTempFile("images", getFileExtension(fileUri));

                TransferUtility transferUtility =
                        TransferUtility.builder()
                                .context(this)
                                .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                                .s3Client(s3Client)
                                .build();

                TransferObserver downloadObserver =
                        transferUtility.download("Aravind_first/" + fileName + "." + getFileExtension(fileUri), localFile);

                downloadObserver.setTransferListener(new TransferListener() {

                    @Override
                    public void onStateChanged(int id, TransferState state) {
                        if (TransferState.COMPLETED == state) {
                            Toast.makeText(getApplicationContext(), "Download Completed!", Toast.LENGTH_SHORT).show();

                            textView.setText(fileName + "." + getFileExtension(fileUri));
                            Bitmap bmp = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                            imageView.setImageBitmap(bmp);
                        }
                    }

                    @Override
                    public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                        float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                        int percentDone = (int) percentDonef;

                        textView.setText("ID:" + id + "|bytesCurrent: " + bytesCurrent + "|bytesTotal: " + bytesTotal + "|" + percentDone + "%");
                    }

                    @Override
                    public void onError(int id, Exception ex) {
                        ex.printStackTrace();
                    }

                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Upload file before downloading", Toast.LENGTH_LONG).show();
        }
    }

        private String getFileExtension (Uri uri){
            ContentResolver contentResolver = getContentResolver();
            MimeTypeMap mime = MimeTypeMap.getSingleton();

            return mime.getExtensionFromMimeType(contentResolver.getType(uri));
        }

        private void createFile (Context applicationContext, Uri fileUri, File file){
            try {
                InputStream inputStream = applicationContext.getContentResolver().openInputStream(fileUri);
                if (inputStream == null) return;
                OutputStream outputStream = new FileOutputStream(file);
                IOUtils.copy(inputStream, outputStream);
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private boolean validateInputFileName (String fileName){
            if (TextUtils.isEmpty(fileName)) {
                Toast.makeText(this, "Enter file name!", Toast.LENGTH_SHORT).show();
                return true;
            }

            return false;
        }

    @Override
    public void onClick(View view) {
        int i = view.getId();

        if (i == R.id.iv1) {
            showChoosingFile();
        } else if (i == R.id.b1) {
            uploadFile();
        }else if (i==R.id.b2){
            downloadFile();
        }
    }
}




