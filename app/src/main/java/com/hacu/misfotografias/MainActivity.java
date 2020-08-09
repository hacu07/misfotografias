package com.hacu.misfotografias;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    private static final int RC_GALLERY = 21;
    private static final int RC_CAMERA = 22;
    private static final int RP_CAMERA = 121; //Request Permision
    private static final int RP_STORAGE = 122;
    private static final String IMAGE_DIRECTORY = "/MyPhotoApp";   // Ruta donde se almacenan las fotos tomadas de la camara
    private static final String MY_PHOTO = "my_photo";// Nombre de foto, nombre local y en nube
    private static final String PATH_PROFILE = "profile";// Direccion ruta en storage firebase
    private static final String PATH_PHOTO_URL = "photoUrl";

    @BindView(R.id.btnUpload)
    Button btnUpload;
    @BindView(R.id.imgPhoto)
    AppCompatImageView imgPhoto;
    @BindView(R.id.btnDelete)
    ImageView btnDelete;
    @BindView(R.id.container)
    ConstraintLayout container;
    private TextView mTextMessage;

    private StorageReference mStorageReference;
    // Guarda URL, si no se sube y se pierde en la nube.
    private DatabaseReference mDatabaseReference;

    private String mCurrentPhotoPath; //Ruta de img tomada desde la camara y para crear en galeria a partir de esta
    private Uri mPhotoSelectedUri;      // Contiene la ruta de la imagen en la galeria, no importa si es tomada de foto o galeria

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mTextMessage = (TextView) findViewById(R.id.message);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        configFirebase();

        configPhotoProfile();
    }

    //Carga la imagen desde el servidor
    private void configPhotoProfile() {
        //OBTENER IMG DESDE STORAGE
        mStorageReference.child(PATH_PROFILE).child(MY_PHOTO).getDownloadUrl()
                .addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        final RequestOptions options = new RequestOptions()
                                .centerCrop()   //CenterCrop: centraliza y corta imagen
                                .diskCacheStrategy(DiskCacheStrategy.ALL);  // Almacena la imagen en la cache (original y procesada que centro y corto)
                        //Se usa la cache para usarla en la app y no volverla a descargar - afecta espacio de la app
                        Glide.with(MainActivity.this)
                                .load(uri)
                                .apply(options)
                                .into(imgPhoto);
                        btnDelete.setVisibility(View.VISIBLE);
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                btnDelete.setVisibility(View.GONE);
                Snackbar.make(container,R.string.main_message_error_notfound,Snackbar.LENGTH_LONG).show();
            }
        });
        //OBTENER IMG DESDE REALTIME DATABASE
        /*mDatabaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                final RequestOptions options = new RequestOptions()
                        .centerCrop()   //CenterCrop: centraliza y corta imagen
                        .diskCacheStrategy(DiskCacheStrategy.ALL);  // Almacena la imagen en la cache (original y procesada que centro y corto)
                //Se usa la cache para usarla en la app y no volverla a descargar - afecta espacio de la app
                Glide.with(MainActivity.this)
                        .load(dataSnapshot.getValue())
                        .apply(options)
                        .into(imgPhoto);
                btnDelete.setVisibility(View.VISIBLE);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                btnDelete.setVisibility(View.GONE);
                Snackbar.make(container,R.string.main_message_error_notfound,Snackbar.LENGTH_LONG).show();
            }
        });*/
    }

    private void configFirebase() {
        mStorageReference = FirebaseStorage.getInstance().getReference();
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        //ruta donde se almacena la URL de la imagen
        mDatabaseReference = database.getReference().child(PATH_PROFILE).child(PATH_PHOTO_URL);
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_gallery:
                    mTextMessage.setText(R.string.main_label_galery);
                    //fromGallery();
                    //VERIFICA SI EL PERMISO ES ACEPTADO
                    checkpermissionToApp(Manifest.permission.READ_EXTERNAL_STORAGE,RP_STORAGE);
                    return true;
                case R.id.navigation_camera:
                    mTextMessage.setText(R.string.main_label_camera);
                    //dispacthTakePictureIntent();
                    //fromCamera();
                    checkpermissionToApp(Manifest.permission.CAMERA,RP_CAMERA);
                    return true;
            }
            return false;
        }
    };


    //Sirve para perdir permisos
    private void checkpermissionToApp(String permissionStr, int requestPermission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,permissionStr) != PackageManager.PERMISSION_GRANTED){
                //Si el permiso no se ha concedido, se solicita
                ActivityCompat.requestPermissions(this,new String[]{permissionStr},requestPermission);
                return; //Corta proceso y queda a la espera de la respuesta del usuario
            }
        }

        switch(requestPermission){
            case RP_STORAGE:
                fromGallery();
                break;
            case RP_CAMERA:
                dispacthTakePictureIntent();
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //Verifica si grantResults no esta vacio - cuando no persionan cancelar
        if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            switch (requestCode){
                case RP_STORAGE:
                    fromGallery();
                    break;
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void dispacthTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null){
            File photoFile;
            photoFile = CreateImageFile();

            if (photoFile != null){
                //Extrae ubicacion del archivo del fileprovider (facilita de forma segura compartir archivos asociados a la app)
                Uri photoUri = FileProvider.getUriForFile(this,
                        "com.hacu.misfotografias",photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,photoUri);
                startActivityForResult(takePictureIntent,RC_CAMERA);
            }
        }
    }

    private File CreateImageFile() {
        //Nombre unico en base a la fecha y hora actual
        final String timestamp = new SimpleDateFormat("dd-MM-yyyy_HHmmss",Locale.ROOT).format(new Date());
         final String imageFileName = MY_PHOTO + timestamp + "_";

         //Directorio unico donde se aloja la imagen
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);//Hace que las fotos sean solo privadas para la app

        File image = null;

        try {
            image = File.createTempFile(imageFileName,".jpg",storageDir);
            //Extrae ruta de la imagen temporal
            mCurrentPhotoPath = image.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return image;
    }

    private void fromCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent,RC_CAMERA);
    }

    //Obtiene img de galeria
    private void fromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);//Activa camara
        startActivityForResult(intent, RC_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            //Controla respuesta desde la galeria o camara
            switch (requestCode) {
                case RC_GALLERY:
                    if (data != null) {
                        mPhotoSelectedUri = data.getData();
                        try {
                            //Objeto bitmap que contine el bitmap segun la Uri especificada
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), mPhotoSelectedUri);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                            imgPhoto.setImageBitmap(bitmap);
                            //Oculta btn de eliminar
                            btnDelete.setVisibility(View.GONE);//Se quita el boton porque solo sirve para eliminar imagenes cuando estan en la nube
                            mTextMessage.setText(R.string.main_message_question_upload);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case RC_CAMERA:
                    /*Bundle extras = data.getExtras();
                    Bitmap bitmap = (Bitmap) extras.get("data");    //Obtiene img capturada de la camara*/
                    mPhotoSelectedUri = addPicGallery();
                    Bitmap bitmap = null;
                    try {
                        bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(),mPhotoSelectedUri);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    imgPhoto.setImageBitmap(bitmap);
                    btnDelete.setVisibility(View.GONE);
                    mTextMessage.setText(R.string.main_message_question_upload);
                    break;
            }
        }
    }

    private Uri addPicGallery() {
        // Indica que se escanee el archivo y lo añade a la BD multimedia del dispositivo -  si no, no aparece en galeria
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File file = new File(mCurrentPhotoPath);
        Uri contentUri = Uri.fromFile(file);    // Envia desde una Uri
        mediaScanIntent.setData(contentUri);    // Envia como parametro
        this.sendBroadcast(mediaScanIntent);
        mCurrentPhotoPath = null;
        return contentUri;
    }

    @OnClick(R.id.btnUpload)
    public void onUploadPhoto() {// Sube foto a servidor de firebase
        Bitmap bitmap = ((BitmapDrawable) imgPhoto.getDrawable()).getBitmap();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] data = baos.toByteArray();
        //1. definir la referencia
        StorageReference profileReference = mStorageReference.child(PATH_PROFILE);
        //Creacion de carpetas
        StorageReference photoReference = profileReference.child(MY_PHOTO);
        UploadTask uploadTask = photoReference.putBytes(data);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Snackbar.make(container, R.string.main_message_upload_error, Snackbar.LENGTH_LONG).show();
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Snackbar.make(container, R.string.main_message_upload_success, Snackbar.LENGTH_LONG).show();
                mTextMessage.setText(R.string.main_message_done);
                btnDelete.setVisibility(View.VISIBLE);
                Uri downloadUri = taskSnapshot.getDownloadUrl();
                savePhotoUrl(downloadUri);
            }
        });
    }

    //Se tiene la foto en el servidor
    //Se inserta URL en BD
    private void savePhotoUrl(Uri downloadUri) {
        mDatabaseReference.setValue(downloadUri.toString());
    }

    @OnClick(R.id.btnDelete)
    public void onDeletePhoto() {
        // Se obtiene al referencia y se elimina el archivo desde storage
        mStorageReference.child(PATH_PROFILE).child(MY_PHOTO).delete()
            .addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    mDatabaseReference.removeValue();
                    imgPhoto.setImageBitmap(null);
                    btnDelete.setVisibility(View.GONE);
                    Snackbar.make(container,R.string.main_message_delete_success,Snackbar.LENGTH_LONG).show();
                }
            }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Snackbar.make(container,R.string.main_message_delete_error,Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
