package tukano.impl.storage;

import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.function.Consumer;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import tukano.api.Result;
import utils.Hash;

import com.azure.core.util.BinaryData;

public class AzureBlobStorage implements BlobStorage{

	// Get connection string in the storage access keys page
	String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=scc70527;AccountKey=Ed/1bDx5OuAwXN/XgvR9wrAH4IpIF9pAxZt0XQqLUOgPKsjcBezUJBdVIypAupi7e6PvGXqhPok4+ASteXd1/g==;EndpointSuffix=core.windows.net";

    private static final String BLOBS_CONTAINER_NAME = "shorts";

    @Override
    public Result<Void> write(String path, byte[] bytes) {
        //if (path == null || !path.contains("/")) //might be needed
        if (path == null)
			return error(BAD_REQUEST);

        try {
            BlobContainerClient containerClient = new BlobContainerClientBuilder()
                .connectionString(storageConnectionString)
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();

            var blob = containerClient.getBlobClient(path);

            if(blob.exists()) {
                var data = blob.downloadContent().toBytes();

                if (Arrays.equals(Hash.sha256(bytes), Hash.sha256(data)))
                    return ok();
                else
                    return error(CONFLICT);
            }

            var data = BinaryData.fromBytes(bytes);
            
            blob.upload(data);

            return ok();
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> delete(String path) {
        //if (path == null || !path.contains("/")) //might be needed
        if (path == null)
			return error(BAD_REQUEST);

        try {
            BlobContainerClient containerClient = new BlobContainerClientBuilder()
                .connectionString(storageConnectionString)
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();

            var blob = containerClient.getBlobClient(path);

            blob.delete();

            return ok();
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<byte[]> read(String path) {
        //if (path == null || !path.contains("/")) //might be needed
        if (path == null)
			return error(BAD_REQUEST);

        try {
            BlobContainerClient containerClient = new BlobContainerClientBuilder()
                .connectionString(storageConnectionString)
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();

            if(!containerClient.exists()){
                return error(NOT_FOUND);
            }

            var blob = containerClient.getBlobClient(path);

            if(!blob.exists()){
                return error(NOT_FOUND);
            }

            var data = blob.downloadContent().toBytes();

            return data != null ? ok( data ) : error( INTERNAL_ERROR );
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        //if (path == null || !path.contains("/")) //might be needed
        if (path == null)
			return error(BAD_REQUEST);
        
        try {
            BlobContainerClient containerClient = new BlobContainerClientBuilder()
                .connectionString(storageConnectionString)
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();

            if(!containerClient.exists()){
                return error(NOT_FOUND);
            }

            var blob = containerClient.getBlobClient(path);

            if(!blob.exists()){
                return error(NOT_FOUND);
            }

            var outputStream = new ByteArrayOutputStream();
            blob.downloadStream(outputStream);
            sink.accept(outputStream.toByteArray());
            return ok();


        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

}