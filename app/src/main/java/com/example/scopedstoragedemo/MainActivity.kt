package com.example.scopedstoragedemo

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

const val PICK_FILE = 1
private const val REQUEST_DELETE_PERMISSION = 831;
private const val REQUEST_DELETE_DOWNLOAD_PERMISSION = 830;

/**
 * @author ypk
 * 创建日期：2020/12/12  10:25
 * 描述：
 */

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val permissionsToRequire = ArrayList<String>()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequire.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequire.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequire.add(Manifest.permission.CAMERA)
        }

        if (!permissionsToRequire.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequire.toTypedArray(), 0)
        }
        browseAlbum.setOnClickListener {
            val intent = Intent(this, BrowseAlbumActivity::class.java)
            startActivity(intent)
        }
        addImageToAlbum.setOnClickListener {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.image)
            //val displayName = "${System.currentTimeMillis()}.jpg"
            //val displayName = "20201130ypk6667.jpg"

            val displayName = "11608686804500_黒板なし.jpg"
            val mimeType = "image/jpeg"
            val compressFormat = Bitmap.CompressFormat.JPEG
            addBitmapToAlbum(bitmap, displayName, mimeType, compressFormat)
        }
        deleteImageToAlbum.setOnClickListener {
            deleteImageFromAlbum();
        }
        downloadFile.setOnClickListener {
            val fileUrl = "http://guolin.tech/android.txt"
            val fileName = "android.txt"
            downloadFile(fileUrl, fileName)
        }
        pickFile.setOnClickListener {
            pickFileAndCopyUriToExternalFilesDir()
        }

        importImageToDownLoad.setOnClickListener {
            importImageToDownLoad();
        }

        deleteImageFromDownLoad.setOnClickListener {
            deleteImageFromDownLoad2();
        }
    }


    private fun deleteImageFromAlbum() {
        val imageFileName = "11608686804500_黒板なし.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val queryPathKey = MediaStore.MediaColumns.DISPLAY_NAME;
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                null,
                "$queryPathKey =? ",
                arrayOf(imageFileName),
                null
            )
            if (cursor != null) {
                Log.e("ypkTest", "cursor is ");
                while (cursor.moveToNext()) {
                    val id =
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val uri =
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    println("ypkTest.deleteFil11e uri=${uri.toString()}")
                    deleteDealWith(uri);
                }
            } else {
                Log.e("ypkTest", "cursor is null");
            }

            /* val where = MediaStore.Images.Media.DISPLAY_NAME + "='" + imageFileName + "'"
             //测试发现，只有是自己应用插入的图片，才可以删除。其他应用的Uri，无法删除。卸载app后，再去删除图片，此方法不会抛出SecurityException异常
             val result = contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, where, null)
             Log.i("ypkTest", "deleteImageFromAlbum1 result=${result}");
             if (result > 0) {
                 Toast.makeText(this, "delete sucess", Toast.LENGTH_LONG).show()
             }*/

        } else {
            val filePath =
                "${Environment.getExternalStorageDirectory().path}/${Environment.DIRECTORY_DCIM}/$imageFileName";
            val where = MediaStore.Images.Media.DATA + "='" + filePath + "'"
            val result =
                contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, where, null)
            Log.i("ypkTest", "result=${result}");
            if (result > 0) {
                Toast.makeText(this, "delete sucess", Toast.LENGTH_LONG).show()
            }

        }


    }

    /**
     * 知识补充：
     * 开了沙箱之后，之前的媒体库生成的文件在其记录上会打上owner_package的标志，标记这条记录是你的app生成的。
     * 当你的app卸载后，MediaStore就会将之前的记录去除owner_package标志，
     * 也就是说app卸载后你之前创建的那个文件与你的app无关了（不能证明是你的app创建的）。
     * 所以当你再次安装app去操作之前的文件时，媒体库会认为这条数据不是你这个新app生成的，所以无权删除或更改。
     * 处理方案：
     * 采用此种方法，删除相册图片，会抛出SecurityException异常，捕获后做下面的处理，会出现系统弹框，提示你是否授权删除。
     * 点击授权后，我们在onActivityResult回调中，再次做删除处理，理论上就能删除。
     *
     * 测试发现：小米8，Android10，是有系统弹框提示，提示是否授权，授权后在去删除，删除的result结果也是1，
     * 根据result的值判断，确实是删除了。但是相册中，依然存在。不知道为何是这样？
     *
     * 参考文章：https://blog.csdn.net/flycatdeng/article/details/105586961
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteDealWith(uri: Uri) {
        try {
            val result = contentResolver.delete(uri, null, null)
            println("ypkTest.deleteImageFromDownLoad result=$result")
            if (result > 0) {
                Toast.makeText(this, "delete  succeeded.", Toast.LENGTH_SHORT).show()
            }
        } catch (securityException: SecurityException) {
            Log.e("ypkTest", "securityException=${securityException.message}");
            securityException.printStackTrace()

            val recoverableSecurityException =
                securityException as? RecoverableSecurityException
                    ?: throw securityException
            // 我们可以使用IntentSender向用户发起授权
            val intentSender =
                recoverableSecurityException.userAction.actionIntent.intentSender
            startIntentSenderForResult(
                intentSender,
                REQUEST_DELETE_PERMISSION,
                null,
                0,
                0,
                0,
                null
            )
        }
    }


    private fun deleteImageFromAlbum1() {
        val imageFileName = "20201130ypk6667.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val where = MediaStore.Images.Media.DISPLAY_NAME + "='" + imageFileName + "'"
            try {
                //测试发现，只有是自己应用插入的图片，才可以删除。其他应用的Uri，无法删除。
                // Android 10+中,如果删除的是其它应用的Uri,则需要用户授权,会抛出RecoverableSecurityException异常,小米8测试，删除失败，并不会抛出异常。
                val result = contentResolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    where,
                    null
                )
                Log.i("ypkTest", "deleteImageFromAlbum1 result=${result}");
                if (result > 0) {
                    Toast.makeText(this, "delete sucess", Toast.LENGTH_LONG).show()
                }
            } catch (securityException: SecurityException) {
                Log.e("ypkTest", "securityException=${securityException.message}");
                securityException.printStackTrace()

                val recoverableSecurityException =
                    securityException as? RecoverableSecurityException ?: throw securityException
                // 我们可以使用IntentSender向用户发起授权
                val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                startIntentSenderForResult(
                    intentSender,
                    REQUEST_DELETE_PERMISSION,
                    null,
                    0,
                    0,
                    0,
                    null
                )
            }
        } else {
            val filePath =
                "${Environment.getExternalStorageDirectory().path}/${Environment.DIRECTORY_DCIM}/$imageFileName";
            val where = MediaStore.Images.Media.DATA + "='" + filePath + "'"
            val result =
                contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, where, null)
            Log.i("ypkTest", "result=${result}");
            if (result > 0) {
                Toast.makeText(this, "delete sucess", Toast.LENGTH_LONG).show()
            }

        }


    }


    private fun importImageToDownLoad() {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.image)
        val fileName = "123456789ypk.jpg"
        val mimeType = "image/jpeg"
        val compressFormat = Bitmap.CompressFormat.JPEG


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val filePath =
                Environment.DIRECTORY_DOWNLOADS + File.separator + BuildConfig.APPLICATION_ID + "/pactera/com/TestFile/"
            val queryPathKey = MediaStore.Downloads.RELATIVE_PATH;
            val cursor = contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                null,
                "$queryPathKey =? ",
                arrayOf(filePath),
                null
            )

            while (cursor!!.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                val name =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME));//图片名字
                val relative_path =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH));//图片名字
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                println("MainActivity.deleteFil11e id=$id")
                println("MainActivity.deleteFil11e name=$name")
                println("MainActivity.deleteFil11e relative_path=$relative_path")
                println("MainActivity.deleteFil11e uri=${uri.toString()}")

                if (fileName == name) {
                    var result = contentResolver.delete(uri, null, null)
                    println("MainActivity.deleteFil11e result=" + result)
                }

            }


            val values = ContentValues()
            values.put(MediaStore.DownloadColumns.DISPLAY_NAME, fileName)
            values.put(MediaStore.DownloadColumns.RELATIVE_PATH, filePath)
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val outputStream = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    bitmap.compress(compressFormat, 100, outputStream)
                    outputStream.close()
                    Toast.makeText(this, "importImage  succeeded.", Toast.LENGTH_SHORT).show()
                }
            }

        } else {


        }

    }

    //用来测试删除文件夹的方法，可先不要删除，暂无合适的解决方案
    private fun deleteImageFromDownLoad4() {
          //  /pactera/com/TestFile/
        val fileName = "123456789ypk.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val filePath = Environment.DIRECTORY_DOWNLOADS + File.separator + BuildConfig.APPLICATION_ID + "/pactera/com/"
            /*
             val queryPathKey = MediaStore.Files.FileColumns.RELATIVE_PATH;
             val queryPathKey2 = MediaStore.Files.FileColumns.DISPLAY_NAME;

             val where = "$queryPathKey = ?"
             //val where = "$queryPathKey = ? and $queryPathKey2 = ? "
             val result = contentResolver.delete(
                 MediaStore.Files.getContentUri("external"),
                 where,
                 arrayOf(filePath)
             )
             Log.i("ypkTest", "deleteImageFromAlbum1 result=${result}");
             if (result > 0) {
                 Toast.makeText(this, "delete sucess", Toast.LENGTH_LONG).show()
             }*/

            var projection: Array<String?>? = null

            val selection: String

            val selectionArgs: Array<String>

            projection =
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.TITLE)

            /* selection = MediaStore.Files.FileColumns.DATA + " like ?"
             selectionArgs = arrayOf("%" + "TestFile" + "/%")*/

            selection = MediaStore.Files.FileColumns.PARENT + " =?";
            selectionArgs = arrayOf("1375903")

            /*var external: Uri? = null
            for (volumeName in MediaStore.getExternalVolumeNames(this)) {
                external = MediaStore.Files.getContentUri(volumeName)
                println("MainActivity.deleteImageFromDownLoad3 volumeName=" + volumeName)
                break
            }*/

            val external = MediaStore.Files.getContentUri("external")
            var cursor = contentResolver.query(
                external,
                null,
                selection,
                selectionArgs,
                null
            );

            while (cursor!!.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID));
                val uri = ContentUris.withAppendedId(external, id);
                println("ypkTest MainActivity.deleteImageFromDownLoad3 uri=$uri")


                val strPARENT = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.PARENT));
                val uriPARENT = ContentUris.withAppendedId(external, strPARENT.toLong());
                println("ypkTest MainActivity.deleteImageFromDownLoad3 uriPARENT=$uriPARENT")

                var result = contentResolver.delete(uri, null, null)
                Log.i("ypkTest", "deleteImageFromAlbum1 result=${result}");
                if (result > 0) {
                    Toast.makeText(this, "delete sucess", Toast.LENGTH_LONG).show()
                }
            }


        }


    }


    //指定删除 xx目录下的xx文件
    private fun deleteImageFromDownLoad() {
        val fileName = "123456789ypk.jpg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val filePath =
                Environment.DIRECTORY_DOWNLOADS + File.separator + BuildConfig.APPLICATION_ID + "/pactera/com/TestFile/"
            val queryPathKey = MediaStore.MediaColumns.RELATIVE_PATH;

            val cursor = contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                null,
                "$queryPathKey =? ",
                arrayOf(filePath),
                null
            )


            if (cursor != null) {
                println("MainActivity.deleteImageFromDownLoad cursor is ")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    val name =
                        cursor.getString(cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME));//图片名字
                    val relative_path =
                        cursor.getString(cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH));//图片名字
                    val uri =
                        ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    println("MainActivity.deleteFil11e id=$id")
                    println("MainActivity.deleteFil11e name=$name")
                    println("MainActivity.deleteFil11e relative_path=$relative_path")
                    println("MainActivity.deleteFil11e uri=${uri.toString()}")

                    if (fileName == name) {
                        try {
                            val result = contentResolver.delete(uri, null, null)
                            println("MainActivity.deleteImageFromDownLoad result=$result")
                            if (result > 0) {
                                Toast.makeText(this, "delete  succeeded.", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        } catch (securityException: SecurityException) {
                            Log.e("ypkTest", "securityException=${securityException.message}");
                            securityException.printStackTrace()

                            val recoverableSecurityException =
                                securityException as? RecoverableSecurityException
                                    ?: throw securityException
                            // 我们可以使用IntentSender向用户发起授权
                            val intentSender =
                                recoverableSecurityException.userAction.actionIntent.intentSender
                            startIntentSenderForResult(
                                intentSender,
                                REQUEST_DELETE_DOWNLOAD_PERMISSION,
                                null,
                                0,
                                0,
                                0,
                                null
                            )

                        } finally {
                        }

                    }
                }
            } else {
                println("MainActivity.deleteImageFromDownLoad cursor is null")
            }


        } else {
            val filePath =
                "/storage/emulated/0" + File.separator + BuildConfig.APPLICATION_ID + "/pactera/com/TestFile";
            val file = File(filePath);
            if (file.isDirectory) {
                file.deleteRecursively();
            }


        }


    }

    //指定删除 xx目录下的xx文件(推荐)
    private fun deleteImageFromDownLoad2() {
        val fileName = "123456789ypk.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val filePath =
                Environment.DIRECTORY_DOWNLOADS + File.separator + BuildConfig.APPLICATION_ID + "/pactera/com/TestFile/"

            val queryPathKey = MediaStore.DownloadColumns.RELATIVE_PATH;
            val queryPathKey2 = MediaStore.DownloadColumns.DISPLAY_NAME;


            //val queryPathKey3 = MediaStore.Files.FileColumns.PARENT;
            val where = "$queryPathKey = ? and $queryPathKey2 = ? "
            val result = contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                where,
                arrayOf(filePath, fileName)
            )
            Log.i("ypkTest", "deleteImageFromAlbum1 result=${result}");
            if (result > 0) {
                Toast.makeText(this, "delete sucess", Toast.LENGTH_LONG).show()
            }

        }
    }

    /**
     * //指定删除 外部储存(external)中的，所有xx文件
     *
     * 使用MediaStore.Files.getContentUri("external")
     * 的方式，去删除文件，只知道文件名，就可以。
     * 因为这是全局扫描，更简单一些。
     *
     */
    private fun deleteImageFromDownLoad3() {
        val fileName = "123456789ypk.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var projection: Array<String?>? =
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.TITLE)

            val selection: String = MediaStore.Files.FileColumns.DISPLAY_NAME + " = ?"
            val selectionArgs: Array<String> = arrayOf(fileName)

            val external = MediaStore.Files.getContentUri("external")
            var cursor = contentResolver.query(
                external,
                null,
                selection,
                selectionArgs,
                null
            );

            while (cursor!!.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID));
                val uri = ContentUris.withAppendedId(external, id);
                println("ypkTest MainActivity.deleteImageFromDownLoad3 uri=$uri")
                var result = contentResolver.delete(uri, null, null)
                Log.i("ypkTest", "deleteImageFromAlbum1 result=${result}");
                if (result > 0) {
                    Toast.makeText(this, "delete sucess", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "You must allow all the permissions.", Toast.LENGTH_SHORT)
                        .show()
                    finish()
                }
            }
        }
    }

    private fun addBitmapToAlbum(
        bitmap: Bitmap,
        displayName: String,
        mimeType: String,
        compressFormat: Bitmap.CompressFormat
    ) {
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        } else {
            values.put(
                MediaStore.MediaColumns.DATA,
                "${Environment.getExternalStorageDirectory().path}/${Environment.DIRECTORY_DCIM}/$displayName"
            )
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            val outputStream = contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                bitmap.compress(compressFormat, 100, outputStream)
                outputStream.close()
                Toast.makeText(this, "Add bitmap to album succeeded.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadFile(fileUrl: String, fileName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(
                this,
                "You must use device running Android 10 or higher",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        thread {
            try {
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val inputStream = connection.inputStream
                val bis = BufferedInputStream(inputStream)
                val values = ContentValues()
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        val bos = BufferedOutputStream(outputStream)
                        val buffer = ByteArray(1024)
                        var bytes = bis.read(buffer)
                        while (bytes >= 0) {
                            bos.write(buffer, 0, bytes)
                            bos.flush()
                            bytes = bis.read(buffer)
                        }
                        bos.close()
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "$fileName is in Download directory now.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                bis.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun pickFileAndCopyUriToExternalFilesDir() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        startActivityForResult(intent, PICK_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PICK_FILE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val uri = data.data
                    if (uri != null) {
                        val fileName = getFileNameByUri(uri)
                        copyUriToExternalFilesDir(uri, fileName)
                    }
                }
            }
            REQUEST_DELETE_PERMISSION -> {
                deleteImageFromAlbum();
            }
            REQUEST_DELETE_DOWNLOAD_PERMISSION -> {
                //deleteImageFromDownLoad()
            }
        }
    }

    private fun getFileNameByUri(uri: Uri): String {
        var fileName = System.currentTimeMillis().toString()
        val cursor = contentResolver.query(uri, null, null, null, null)
        if (cursor != null && cursor.count > 0) {
            cursor.moveToFirst()
            fileName =
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
            cursor.close()
        }
        return fileName
    }

    private fun copyUriToExternalFilesDir(uri: Uri, fileName: String) {
        thread {
            val inputStream = contentResolver.openInputStream(uri)
            val tempDir = getExternalFilesDir("temp")
            if (inputStream != null && tempDir != null) {
                val file = File("$tempDir/$fileName")
                val fos = FileOutputStream(file)
                val bis = BufferedInputStream(inputStream)
                val bos = BufferedOutputStream(fos)
                val byteArray = ByteArray(1024)
                var bytes = bis.read(byteArray)
                while (bytes > 0) {
                    bos.write(byteArray, 0, bytes)
                    bos.flush()
                    bytes = bis.read(byteArray)
                }
                bos.close()
                fos.close()
                runOnUiThread {
                    Toast.makeText(this, "Copy file into $tempDir succeeded.", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

}
