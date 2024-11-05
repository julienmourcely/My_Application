package com.example.myapplication.ui.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.telephony.SmsManager;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int LOCATION_PERMISSION_CODE = 101;

    private static final int REQUEST_SEND_SMS = 102;
    private Button btnPicture;
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private ImageReader imageReader;
    private FusedLocationProviderClient fusedLocationClient; // Client de localisation

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        btnPicture = view.findViewById(R.id.btncamera_id);
        textureView = view.findViewById(R.id.texture_view);

        // Initialiser le client de localisation
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        checkPermissions();

        btnPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePictureAndCalculateCoordinates();
            }
        });

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closeCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });

        return view;
    }

    private void checkPermissions() {
        // Vérifie la permission de la caméra
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

        // Vérifie la permission de localisation
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        }
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.SEND_SMS}, REQUEST_SEND_SMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[0]; // Get the first camera
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Size[] sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class);

            Size optimalSize = getOptimalPreviewSize(sizes, textureView.getWidth(), textureView.getHeight());

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    startCameraPreview(optimalSize);  // Passer la taille optimale
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void startCameraPreview(Size optimalSize) {
        if (optimalSize == null) {
            optimalSize = new Size(640, 480); // Taille par défaut si optimalSize est null
        }

        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture == null) {
                return;
            }

            // Utiliser la taille optimale ou par défaut
            surfaceTexture.setDefaultBufferSize(optimalSize.getWidth(), optimalSize.getHeight());
            Surface surface = new Surface(surfaceTexture);

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSession = session;
                    try {
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                            }
                        }, null);

                        // Appeler configureTransform ici
                        configureTransform(textureView.getWidth(), textureView.getHeight());
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(requireContext(), "Camera configuration failed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }




    private int getJpegOrientation(CameraCharacteristics c) {
        int deviceRotation = requireActivity().getWindowManager().getDefaultDisplay().getRotation();
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceRotation = (deviceRotation == Surface.ROTATION_0) ? 90 :
                (deviceRotation == Surface.ROTATION_90) ? 0 :
                        (deviceRotation == Surface.ROTATION_180) ? 270 : 180;
        return (sensorOrientation + deviceRotation + 270) % 360;
    }

    @SuppressLint("MissingPermission")
    private void takePictureAndCalculateCoordinates() {
        takePicture();

        fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    // Coordonnées GPS réelles de l'utilisateur
                    Point userLocation = new Point(location.getLatitude(), location.getLongitude());

                    // Calcul de la coordonnée cible avec un azimut et une distance
                    double azimut = 45.0; // Exemple d'azimut
                    double distance = 100.0; // Exemple de distance en mètres
                    //Point targetPoint = userLocation.destination(distance, azimut);
                    Point targetPoint = userLocation;

                    String message = String.format(Locale.getDefault(),
                            "Coordonnées cibles :\nLatitude : %.5f\nLongitude : %.5f\nAltitude : %.2f m",
                            targetPoint.lat, targetPoint.lon, targetPoint.alt);

                    // Changer le numéro si nécessaire
                    sendSMS("num", message);


                    // Mettre à jour le texte du bouton avec les coordonnées cibles
                    btnPicture.setText(String.format(Locale.getDefault(), "Lat: %.5f, Lon: %.5f", targetPoint.lat, targetPoint.lon));
                } else {
                    Toast.makeText(requireContext(), "Impossible d'obtenir la localisation", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void takePicture() {
        if (cameraDevice == null) return;

        Log.d("HomeFragment", "Le bouton pour prendre une photo a été pressé.");

        CameraManager cameraManager = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);
            }

            int width = 640; // Taille par défaut
            int height = 480;
            if (jpegSizes != null && jpegSizes.length > 0) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
            Surface surface = new Surface(texture);
            Surface imageSurface = imageReader.getSurface();

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageSurface);

            // Orientation de la capture
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation(characteristics));

            cameraDevice.createCaptureSession(Arrays.asList(surface, imageSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                saveImage(); // Enregistre l'image capturée
                                openCamera(); // Relance la caméra après capture
                            }
                        }, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(requireContext(), "Failed to configure camera", Toast.LENGTH_SHORT).show();
                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void saveImage() {
        Image image = imageReader.acquireLatestImage();
        if (image != null) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FireApp");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File file = new File(directory, "photo_" + timeStamp + ".jpg");

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
                Toast.makeText(requireContext(), "Image saved: " + file.getPath(), Toast.LENGTH_SHORT).show();
                // Changer le numéro si nécessaire
                sendMmsWithImage(file, "num");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                image.close();
            }
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null || cameraDevice == null) {
            return;
        }

        Matrix matrix = new Matrix();
        int rotation = requireActivity().getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0, 0, viewWidth, viewHeight);
        RectF previewRectF = new RectF(0, 0, viewHeight, viewWidth);

        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            previewRectF.offset(centerX - previewRectF.centerX(), centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / textureRectF.height(),
                    (float) viewWidth / textureRectF.width());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private Size getOptimalPreviewSize(Size[] sizes, int width, int height) {
        if (sizes == null || sizes.length == 0) {
            // Si aucune taille n'est disponible, retourner null ou une taille par défaut
            return new Size(640, 480); // Taille par défaut
        }

        double targetRatio = (double) height / width;
        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        for (Size size : sizes) {
            double ratio = (double) size.getWidth() / size.getHeight();
            if (Math.abs(ratio - targetRatio) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(ratio - targetRatio);
            }
        }

        // Retourner la taille optimale ou une taille par défaut si aucune n'est trouvée
        return optimalSize != null ? optimalSize : new Size(640, 480);
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void sendSMS(String phoneNumber, String message) {
        SmsManager smsManager = SmsManager.getDefault();
        try {
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(requireContext(), "SMS envoyé avec succès", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Échec de l'envoi du SMS", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void sendMmsWithImage(File imageFile, String phoneNumber) {
        Uri imageUri = FileProvider.getUriForFile(requireContext(), "com.example.myapplication.fileprovider", imageFile);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/jpeg");
        intent.putExtra("address", phoneNumber);
        intent.putExtra(Intent.EXTRA_STREAM, imageUri);
        intent.putExtra(Intent.EXTRA_TEXT, "Voici l'image.");

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Ouvre l'application de messagerie avec l'image et le texte en pré-rempli
        startActivity(Intent.createChooser(intent, "Envoyer MMS via"));
    }

    @Override
    public void onPause() {
        super.onPause();
        closeCamera(); // Close the camera when the fragment is paused
    }

    @Override
    public void onResume() {
        super.onResume();
        openCamera(); // Close the camera when the fragment is paused
    }

    @Override
    public void onStart() {
        super.onStart();
        openCamera();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeCamera();
    }

}