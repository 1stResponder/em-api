/**
 * Copyright (c) 2008-2016, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.mit.ll.em.api.rs.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import edu.mit.ll.em.api.dataaccess.SystemRoleDAO;
import edu.mit.ll.nics.common.entity.CollabRoom;
import edu.mit.ll.nics.common.entity.datalayer.*;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.codehaus.jackson.map.ObjectMapper;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.referencing.FactoryException;

import edu.mit.ll.em.api.dataaccess.ShapefileDAO;
import edu.mit.ll.em.api.dataaccess.UserOrgDAO;
import edu.mit.ll.em.api.rs.DatalayerDocumentServiceResponse;
import edu.mit.ll.em.api.rs.DatalayerService;
import edu.mit.ll.em.api.rs.DatalayerServiceResponse;
import edu.mit.ll.em.api.rs.FieldMapResponse;
import edu.mit.ll.em.api.util.APIConfig;
import edu.mit.ll.em.api.util.FileUtil;
import edu.mit.ll.em.api.util.ImageLayerGenerator;
import edu.mit.ll.em.api.util.SADisplayConstants;
import edu.mit.ll.nics.common.entity.User;
import edu.mit.ll.nics.common.entity.UserOrg;
import edu.mit.ll.nics.common.entity.datalayer.Datalayer;
import edu.mit.ll.nics.common.entity.datalayer.Datalayerfolder;
import edu.mit.ll.nics.common.entity.datalayer.Datalayersource;
import edu.mit.ll.nics.common.entity.datalayer.Datasource;
import edu.mit.ll.nics.common.entity.datalayer.Document;
import edu.mit.ll.nics.common.entity.datalayer.Rootfolder;
import edu.mit.ll.nics.common.geoserver.api.GeoServer;
import edu.mit.ll.nics.common.rabbitmq.RabbitFactory;
import edu.mit.ll.nics.common.rabbitmq.RabbitPubSubProducer;
import edu.mit.ll.nics.nicsdao.DatalayerDAO;
import edu.mit.ll.nics.nicsdao.DocumentDAO;
import edu.mit.ll.nics.nicsdao.FolderDAO;
import edu.mit.ll.nics.nicsdao.UserDAO;
import edu.mit.ll.nics.nicsdao.impl.DatalayerDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.DocumentDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.FolderDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserOrgDAOImpl;
import edu.mit.ll.nics.nicsdao.impl.UserSessionDAOImpl;

import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;

import edu.mit.ll.nics.tools.image_processing.ImageProcessor;

/**
 * 
 * @AUTHOR st23420
 *
 */
public class DatalayerServiceImpl implements DatalayerService {

	private static final Log logger = LogFactory.getLog(DatalayerServiceImpl.class);
	
	/** A standard KML root element. */
	private static final String KML_ROOT_START_TAG =
			"<kml xmlns=\"http://www.opengis.net/kml/2.2\" " +
			"xmlns:gx=\"http://www.google.com/kml/ext/2.2\" " +
			"xmlns:kml=\"http://www.opengis.net/kml/2.2\" " +
			"xmlns:atom=\"http://www.w3.org/2005/Atom\">";
	
	/** A pattern that matches KML documents without a root <kml> element. */
	private static final Pattern MALFORMED_KML_PATTERN = Pattern.compile("^\\s*<\\?xml[^>]+>\\s*<Document>", Pattern.MULTILINE);
	
	/** Folder DAO */
	private static final DatalayerDAO datalayerDao = new DatalayerDAOImpl();
	private static final FolderDAO folderDao = new FolderDAOImpl();
	private static final DocumentDAO documentDao = new DocumentDAOImpl();
	private static final UserDAO userDao = new UserDAOImpl();
	private static final UserOrgDAOImpl userOrgDao = new UserOrgDAOImpl();
	private static final UserSessionDAOImpl usersessionDao = new UserSessionDAOImpl();
	
	private static String fileUploadPath;
	private static String mapserverURL;
	private static String geoserverWorkspace;
	private static String geoserverDatastore;
	private static String webserverURL;
	private static String tempDir;
	
	private RabbitPubSubProducer rabbitProducer;
	
	private final Client jerseyClient;

	public DatalayerServiceImpl() {
		Configuration config = APIConfig.getInstance().getConfiguration();
		fileUploadPath = config.getString(APIConfig.FILE_UPLOAD_PATH, "/opt/data/nics/upload");
		geoserverWorkspace = config.getString(APIConfig.IMPORT_SHAPEFILE_WORKSPACE, "nics");
		geoserverDatastore = config.getString(APIConfig.IMPORT_SHAPEFILE_STORE, "shapefiles");
		mapserverURL = config.getString(APIConfig.EXPORT_MAPSERVER_URL);
		webserverURL = config.getString(APIConfig.EXPORT_WEBSERVER_URL);
		jerseyClient = ClientBuilder.newClient();
		tempDir = System.getProperty("java.io.tmpdir");
	}
	
	@Override
	public Response getDatalayers(String folderId) {
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		try{
			datalayerResponse.setDatalayerfolders(datalayerDao.getDatalayerFolders(folderId));
		}catch(Exception e){
			logger.error("Failed to retrieve data layers", e);
			datalayerResponse.setMessage("Failed to retrieve data layers");
			return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		return Response.ok(datalayerResponse).status(Status.OK).build();
	}
	
	public Response getCollabRoomDatalayers(int collabRoomId){
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		try{
			datalayerResponse.setDatalayers(datalayerDao.getCollabRoomDatalayers(collabRoomId));
		}catch(Exception e){
			logger.error("Failed to retrieve data layers", e);
			datalayerResponse.setMessage("Failed to retrieve data layers");
			return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		return Response.ok(datalayerResponse).status(Status.OK).build();
	}
	
	
	@Override
	public Response getTrackingLayers(int workspaceId) {
		FieldMapResponse response = new FieldMapResponse();
		try{
			List<Map<String,Object>> layers = datalayerDao.getTrackingLayers(workspaceId, true);
			layers.addAll(datalayerDao.getTrackingLayers(workspaceId, false));
			response.setData(layers);
		}catch(Exception e){
			logger.error("Failed to retrieve data layers", e);
			response.setMessage("Failed to retrieve data layers");
			return Response.ok(response).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return Response.ok(response).status(Status.OK).build();
	}
	
	@Override
	public Response getDatasources(String type) {
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		try{
			datalayerResponse.setDatasources(datalayerDao.getDatasources(type));
		}catch(Exception e){
			logger.error("Failed to retrieve data sources", e);
			datalayerResponse.setMessage("Failed to retrieve data sources");
			return Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		return Response.ok(datalayerResponse).status(Status.OK).build();
	}
	
	@Override
	public Response postDatasource(String type, Datasource source) {
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		Response response = null;
		
		try{
			int dataSourceTypeId = datalayerDao.getDatasourceTypeId(type);
			source.setDatasourcetypeid(dataSourceTypeId);
			
			String dataSourceId = datalayerDao.insertDataSource(source);
			Datasource newSource = datalayerDao.getDatasource(dataSourceId);
			datalayerResponse.setDatasources(Arrays.asList(newSource));
			datalayerResponse.setMessage("ok");
			response = Response.ok(datalayerResponse).status(Status.OK).build();
		} catch(Exception e) {
			logger.error("Failed to insert data source", e);
			datalayerResponse.setMessage("failed");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		return response;
	}

	@Override
	public Response postDataLayer(int workspaceId, String dataSourceId, Datalayer datalayer, String username) {
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		Response response = null;
		Datalayerfolder newDatalayerFolder = null;
		
		try{
			datalayer.setCreated(new Date());
			datalayer.getDatalayersource().setCreated(new Date());
			datalayer.getDatalayersource().setDatasourceid(dataSourceId);
			
			String datalayerId = datalayerDao.insertDataLayer(dataSourceId, datalayer);

			int orgId = -1;
			Set<DatalayerOrg> orgs = datalayer.getDatalayerOrgs();

			if (orgs != null) {
				for (DatalayerOrg org : orgs) {
					orgId = org.getOrgid();
					datalayerDao.insertDatalayerOrg(datalayerId, orgId);
				}

			}

			int collabroomId = -1;
			Set<DatalayerCollabroom> collabrooms = datalayer.getDatalayerCollabrooms();

			if (collabrooms.size() == 1) {

				// Upload to collab room only

				for (DatalayerCollabroom collabroom : collabrooms) {
					collabroomId = collabroom.getCollabroomid();
				}
				Response collabroomResponse = addCollabroomDatalayer(collabroomId, datalayerId, username);
				// TODO: check collabroomResponse for success

				datalayerResponse.setMessage("ok");
				response = Response.ok(datalayerResponse).status(Status.OK).build();
			}
			else
			{
				// Upload to master datalayer list

				//Currently always uploads to Data
				//Rootfolder folder = folderDao.getRootFolder("Data", workspaceId);
				//Currently always uploads to Upload
				Folder folder = folderDao.getFolderByName("Upload", workspaceId);
				int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folder.getFolderid());
				datalayerDao.insertDataLayerFolder(folder.getFolderid(), datalayerId, nextFolderIndex);
				newDatalayerFolder = datalayerDao.getDatalayerfolder(datalayerId, folder.getFolderid());

				datalayerResponse.setDatalayerfolders(Arrays.asList(newDatalayerFolder));
				datalayerResponse.setMessage("ok");
				response = Response.ok(datalayerResponse).status(Status.OK).build();
			}
		}
		catch(Exception e) {
			logger.error("Failed to insert data layer", e);
			datalayerResponse.setMessage("failed");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				notifyNewChange(newDatalayerFolder, workspaceId);
			} catch (IOException e) {
				logger.error("Failed to publish DatalayerService message event", e);
			}
		}
		
		return response;
	}
	
	@Override
	public Response deleteDataLayer(int workspaceId, String dataSourceId){
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		Response response = null;
		boolean deleteDatalayer = false;
		
		try{
		
			deleteDatalayer = datalayerDao.removeDataLayer(dataSourceId);
		
			if(deleteDatalayer){
				datalayerResponse.setMessage("OK");
				response = Response.ok(datalayerResponse).status(Status.OK).build();	
			}
			else{
				datalayerResponse.setMessage("Failed to delete datalayer");
				response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
			
		
		}catch(Exception e){
			logger.error("Failed to delete data layer", e);
			datalayerResponse.setMessage("Failed to delete datalayer");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				notifyDeleteChange(dataSourceId);
			} catch (IOException e) {
				logger.error("Failed to publish DatalayerService message event", e);
			}
		}
		
		return response;
	}
	
	public Response updateDataLayer(int workspaceId, Datalayer datalayer){
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		Response response = null;
		Datalayer dbDatalayer = null;
		
		try{
		
			dbDatalayer = datalayerDao.updateDataLayer(datalayer);
		
			if(dbDatalayer != null){
				datalayerResponse.setCount(1);
				datalayerResponse.setMessage("OK");
				response = Response.ok(datalayerResponse).status(Status.OK).build();	
			}
			else{
				datalayerResponse.setMessage("Failed to update datalayer");
				response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
			
		
		}catch(Exception e){
			logger.error("Failed to delete data layer", e);
			datalayerResponse.setMessage("Failed to delete datalayer");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				notifyUpdateChange(dbDatalayer);
			} catch (IOException e) {
				logger.error("Failed to publish DatalayerService message event", e);
			}
		}
		
		datalayerResponse.setMessage("OK");
		response = Response.ok(datalayerResponse).status(Status.OK).build();	
		
		return response;
	}
	
	public Response postShapeDataLayer(int workspaceId, String displayName, MultipartBody body, String username) {
		if(!userOrgDao.isUserRole(username, SADisplayConstants.SUPER_ROLE_ID) &&
				!userOrgDao.isUserRole(username, SADisplayConstants.ADMIN_ROLE_ID) &&
				!userOrgDao.isUserRole(username, SADisplayConstants.GIS_ROLE_ID)){
			logger.error("Permission Denied");
			return getInvalidResponse();
		}
			
		ShapefileDAO geoserverDao = ShapefileDAO.getInstance();
		GeoServer geoserver = getGeoServer(APIConfig.getInstance().getConfiguration());
		String dataSourceId = getMapserverDatasourceId();
		if (dataSourceId == null) {
			logger.error("Failed to find configured NICS wms datasource");
			throw new WebApplicationException("Failed to find configured NICS wms datasource");
		}
		
		Attachment aShape = body.getAttachment("shpFile");
		if (aShape == null) {
			logger.error("Required attachment 'shpFile' not found");
			throw new WebApplicationException("Required attachment 'shpFile' not found");
		}
		String shpFilename = aShape.getContentDisposition().getParameter("filename");
		String batchName = shpFilename.replace(".shp", "").replace(" ", "_");
		String layerName = batchName.concat(String.valueOf(System.currentTimeMillis()));
		
		//write all the uploaded files to the filesystem in a temp directory
		Path shapesDirectory = Paths.get(fileUploadPath, "/shapefiles");
		Path batchDirectory = null;
		try {
			Files.createDirectories(shapesDirectory);
			
			batchDirectory = Files.createTempDirectory(shapesDirectory, batchName);
			List<Attachment> attachments = body.getAllAttachments();
			for(Attachment attachment : attachments) {
				String filename = attachment.getContentDisposition().getParameter("filename");
				String extension = FileUtil.getFileExtension(filename);
				if (extension != null) {
					Path path = batchDirectory.resolve(batchName.concat(extension));
					InputStream is = attachment.getDataHandler().getInputStream();
					Files.copy(is, path);
				}
			}
			
			//attempt to read our shapefile and accompanying files
			Path shpPath = batchDirectory.resolve(batchName.concat(".shp"));
			FileDataStore store = FileDataStoreFinder.getDataStore(shpPath.toFile());
			SimpleFeatureSource featureSource = store.getFeatureSource();
			
			//attempt to insert our features into their own table
			geoserverDao.insertFeatures(layerName, featureSource);
		} catch (IOException | FactoryException e) {
			try {
				geoserverDao.removeFeaturesTable(layerName);
			} catch (IOException ioe) { /* bury */}
			logger.error("Failed to import shapefile", e);
			throw new WebApplicationException("Failed to import shapefile", e);
		} finally {
			//always clean up our temp directory
			if (batchDirectory != null) {
				try {
					FileUtil.deleteRecursively(batchDirectory);
				} catch (IOException e) {
					logger.error("Failed to cleanup shapefile batch directory", e);
					logger.error("Failed to cleanup shapefile batch directory", e);
				}
			}
		}
		
		//add postgis layer to map server
		if(!geoserver.addFeatureType(geoserverWorkspace, geoserverDatastore, layerName, "EPSG:3857")){
			try {
				geoserverDao.removeFeaturesTable(layerName);
			} catch (IOException e) { /* bury */}
			logger.error("Failed to create features " + layerName);
			throw new WebApplicationException("Failed to create features " + layerName);
		}
		
		//apply styling default or custom sld
		String defaultStyleName = "defaultShapefileStyle";
		Attachment aSld = body.getAttachment("sldFile");
		if (aSld != null) {
			String sldXml = aSld.getObject(String.class);
			if (geoserver.addStyle(layerName, sldXml) ) {
				defaultStyleName = layerName;
			}
		}
		geoserver.updateLayerStyle(layerName, defaultStyleName);
		geoserver.updateLayerEnabled(layerName, true);

		//create datalayer and datalayersource for our new layer 
		int usersessionid = usersessionDao.getUserSessionid(username);
		
		Datalayer datalayer = new Datalayer(); 
		datalayer.setCreated(new Date());
		datalayer.setBaselayer(false);
		datalayer.setDisplayname(displayName);
		datalayer.setUsersessionid(usersessionid);
		
		Datalayersource dlsource = new Datalayersource();
		dlsource.setLayername(layerName);
		dlsource.setCreated(new Date());
		dlsource.setDatasourceid(dataSourceId);
		datalayer.setDatalayersource(dlsource);
		
		String datalayerId = datalayerDao.insertDataLayer(dataSourceId, datalayer);
		//Rootfolder folder = folderDao.getRootFolder("Data", workspaceId);
		Folder folder = folderDao.getFolderByName("Upload", workspaceId);
		int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folder.getFolderid());
		datalayerDao.insertDataLayerFolder(folder.getFolderid(), datalayerId, nextFolderIndex);

		//retrieve the new datalayerfolder to return to the client and broadcast
		Datalayerfolder newDatalayerFolder = datalayerDao.getDatalayerfolder(datalayerId, folder.getFolderid());
		
		try {
			notifyNewChange(newDatalayerFolder, workspaceId);
		} catch (IOException e) {
			logger.error("Failed to publish DatalayerService message event", e);
		}
		
		DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
		datalayerResponse.setSuccess(true);
		datalayerResponse.setCount(1);
		datalayerResponse.setDatalayerfolders(Arrays.asList(newDatalayerFolder));
		return Response.ok(datalayerResponse).status(Status.OK).build();
	}
	
	public Response postImageDataLayer(int workspaceId, String id, MultipartBody body, String username) {
		DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
		boolean imageFound = false;
		
		for(Attachment attachment : body.getAllAttachments()) {	
			
			if(attachment.getContentType().getType().contains("image")){
				imageFound = true;
				
				StringBuffer filePath = new StringBuffer(
						APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_FEATURE_PATH,"/opt/data/nics/upload/images"));
				filePath.append("/");
				filePath.append(id);
				
				System.out.println("FILE PATH : " + filePath.toString());
				

				//Path path = this.createFile(attachment, Paths.get(filePath.toString()));
				Path path = this.createFile(attachment, filePath.toString());
				
				if(path != null){
					System.out.println("PATH : " + filePath.toString() + "/" + path.getFileName());

					GeoLocation location  = null;
					
					try {
						location = ImageProcessor.getLocation(filePath.toString() + "/" + path.getFileName());
					} catch (Exception e)
					{
						System.out.println("ERROR in DataLaterService: " + e.getMessage());
						logger.error("Failed to get location: ",e);
					}
					
					if(location != null){
						StringBuffer locationString = new StringBuffer("POINT(");
						locationString.append(location.getLongitude());
						locationString.append(" ");
						locationString.append(location.getLatitude());
						locationString.append(")");
						
						System.out.println("LOCATION : " + location.getLongitude() + "," + location.getLatitude());
						
						String tempLoc = locationString.toString();
						String tempfilename = id + "/" + path.getFileName().toString();

						System.out.println("ID: " + id);
						System.out.println("location: " + tempLoc);
						System.out.println("filename: " + tempfilename);

						if(datalayerDao.insertImageFeature(
								id, locationString.toString(), id + "/" + path.getFileName().toString()) == 1){
							datalayerResponse.setSuccess(true);
							datalayerResponse.setCount(1);
						}else{
							datalayerResponse.setSuccess(false);
							datalayerResponse.setMessage("There was an error persisting the image.");
						}
					}else{
						System.out.println("No location found for the image...");
						datalayerResponse.setSuccess(false);
						datalayerResponse.setMessage("No location found for image.");
					}
				}else{
					datalayerResponse.setSuccess(false);
					datalayerResponse.setMessage("There was an error creating the directory.");
				}
			}
			
			if(!imageFound){
				datalayerResponse.setSuccess(false);
				datalayerResponse.setMessage("No image found.");
			}
		}
		
		return Response.ok(datalayerResponse).status(Status.OK).build();
	}
	
	public Response finishImageLayer(boolean cancel, int workspaceId, String id, String title, int usersessionId, String username){

		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		if(!cancel){
			ImageLayerGenerator generator = new ImageLayerGenerator(
					APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_MAPSERVER_URL), 
					APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_MAPSERVER_USERNAME),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_MAPSERVER_PASSWORD),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_MAPSERVER_WORKSPACE), 
					APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_MAPSERVER_STORE));
			
			boolean layerCreated = generator.addImageLayer(id, title);
			
			System.out.println("Layer Created : " + layerCreated);
			
			if(layerCreated){
				String datasourceId = datalayerDao.getDatasourceId(
						APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_LAYER_DATASOURCE_URL));
				
				Datalayer datalayer = new Datalayer();
				datalayer.setBaselayer(false);
				datalayer.setCreated(new Date());
				datalayer.setDisplayname(title);
				datalayer.setUsersessionid(usersessionId);
				
				Datalayersource datalayerSource = new Datalayersource();
				datalayerSource.setLayername(id);
				datalayer.setDatalayersource(datalayerSource);
				
				System.out.println("Post Datalayer : " + workspaceId + "," + datasourceId + "," + datalayer.getDisplayname());
				
				return this.postDataLayer(workspaceId, datasourceId, datalayer, username);
			}
		}else{
			StringBuffer responseMessage = new StringBuffer();
			//Remove the image files from the file system
			StringBuffer filePath = new StringBuffer(
					APIConfig.getInstance().getConfiguration().getString(APIConfig.IMAGE_FEATURE_PATH,"/opt/data/nics/upload/images"));
			filePath.append("/");
			filePath.append(id);
			
			Path path = Paths.get(filePath.toString());
			
			try {
			    Files.delete(path);
			} catch (NoSuchFileException x) {
				responseMessage.append(String.format("%s: no such" + " file or directory%n", path));
			} catch (DirectoryNotEmptyException x) {
				responseMessage.append(String.format("%s not empty%n", path));
			} catch (IOException x) {
				responseMessage.append(x.getMessage());
			}
			
			if(responseMessage.length() != 0){
				responseMessage.append(System.getProperty("line.separator"));
			}
			
			//Remove all imagefeature entries from the database
			int removed = this.datalayerDao.removeImageFeatures(id);
			if(removed < 1){
				responseMessage.append("The images could not be removed from the database.");
			}
			
			if(responseMessage.length() != 0){
				datalayerResponse.setMessage(responseMessage.toString());
			}
		}
		
		return Response.ok(datalayerResponse).status(Status.OK).build();
	}
	
	public Response postDataLayerDocument(int workspaceId, String fileExt, int userOrgId, int refreshRate, MultipartBody body, String username){
		
		DatalayerDocumentServiceResponse datalayerResponse = new DatalayerDocumentServiceResponse();
		Response response = null;
		Datalayerfolder newDatalayerFolder = null;
		Datalayer datalayer = new Datalayer();
		datalayer.setDatalayersource(new Datalayersource());
		String dataSourceId = null;
		Document doc = null;
		Boolean uploadedDataLayer = false;
		String fileName = null;
		String filePath = null;
		Boolean valid = false;
		User user = null;
		int orgId = -1;
		int collabroomId = -1;

		logger.error("My Username is: " + username);
		logger.error("My ext is: " + fileExt);

		try{

			user = userDao.getUser(username);
			Set<UserOrg> userOrgs = user.getUserorgs();
			Iterator<UserOrg> iter = userOrgs.iterator();
			
			while(iter.hasNext()){

				UserOrg userOrg = (UserOrg)iter.next();
				
				if(userOrg.getUserorgid() == userOrgId && 
						(userOrg.getSystemroleid() == SADisplayConstants.SUPER_ROLE_ID || 
						userOrg.getSystemroleid() == SADisplayConstants.GIS_ROLE_ID ||
						userOrg.getSystemroleid() == SADisplayConstants.ADMIN_ROLE_ID	)){
					valid = true;
				}
				
			}

			if(!valid){
				return getInvalidResponse();
			}
			
			for(Attachment attachment : body.getAllAttachments()) {
				
				Object propValue = attachment.getObject(String.class).toString();
	
				if(MediaType.TEXT_PLAIN_TYPE.isCompatible(attachment.getContentType())){

					logger.error("text file");

					if(attachment.getContentDisposition().getParameter("name").toString().equals("usersessionid")){
						datalayer.setUsersessionid(Integer.valueOf(propValue.toString()));
					}
					else if(attachment.getContentDisposition().getParameter("name").toString().equals("displayname")){
						datalayer.setDisplayname(propValue.toString());
					}	
					else if(attachment.getContentDisposition().getParameter("name").toString().equals("baselayer")){
						datalayer.setBaselayer(Boolean.parseBoolean(propValue.toString()));
					}
					else if(attachment.getContentDisposition().getParameter("name").equals("orgid")) {
						try
						{
							orgId = Integer.parseInt(propValue.toString());
						} catch (NumberFormatException ex)
						{
							// orgId isn't an integer; datalayer should not be restricted to an organization
						}
					}
					else if (attachment.getContentDisposition().getParameter("name").equals("collabroomId")) {
						try
						{
							collabroomId = Integer.parseInt(propValue.toString());
							System.out.println("============= collabroomId="+collabroomId);
						} catch (NumberFormatException ex)
						{
							//
						}
					}
				}
				else{

					logger.error("not text");

					String attachmentFilename = attachment.getContentDisposition().getParameter("filename").toLowerCase();
					if (attachmentFilename.endsWith(".kmz")){
						logger.error("kmz file");
						filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.KMZ_UPLOAD_PATH,"/opt/data/nics/upload/kmz");
					} else if (attachmentFilename.endsWith(".gpx")){
						logger.error("gpx file");
						filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.GPX_UPLOAD_PATH,"/opt/data/nics/upload/gpx");
					} else if (attachmentFilename.endsWith(".json") || attachmentFilename.endsWith(".geojson")){
						logger.error("geojson file");
					 	filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.JSON_UPLOAD_PATH,"/opt/data/nics/upload/geojson");
					} else if (attachmentFilename.endsWith(".kml")){
						logger.error("kml file");
						filePath = APIConfig.getInstance().getConfiguration().getString(APIConfig.KML_UPLOAD_PATH,"/opt/data/nics/upload/kml");
					}
					
					if(filePath != null){
						try {
							doc = getDocument(attachment, Paths.get(filePath));
							} catch (Exception e)
							{
								logger.error("Document could not be created",e);
							}
					} else
					{
						logger.error("File path is null!");
					}
				}
			}
			
			if(doc != null){
				
				doc.setUsersessionid(datalayer.getUsersessionid());
				doc = documentDao.addDocument(doc);
				
				dataSourceId = getFileDatasourceId(fileExt);
				
				if (dataSourceId != null) {
					datalayer.setCreated(new Date());
					datalayer.getDatalayersource().setCreated(new Date());
					datalayer.getDatalayersource().setDatasourceid(dataSourceId);
					datalayer.getDatalayersource().setRefreshrate(refreshRate);
				}
				
				String docFilename = doc.getFilename().toLowerCase();
				
				if (uploadedDataLayer = docFilename.endsWith(".kmz")) {
					String subdir = docFilename.substring(0, docFilename.length() - 4);
					Path kmzDir = Paths.get(filePath, subdir);
					if (! Files.exists(kmzDir))
						Files.createDirectory(kmzDir);
					
					try (
						FileInputStream fis = new FileInputStream(filePath + doc.getFilename());
						ZipInputStream zipStream = new ZipInputStream(fis)
					) {
						ZipEntry entry;
						
						// Stream all KMZ entries into new files under this temp dir.
						while((entry = zipStream.getNextEntry()) != null) {
							if (entry.getSize() == 0){ 				             
								 continue; 				            
							}
							
							String entryName = entry.getName();
							Path outPath = kmzDir.resolve(entryName);
				            
				            if (entryName.toLowerCase().endsWith(".kml"))
				            	fileName = entryName;
				            	
				            if (entryName.contains("/"))
				            	Files.createDirectories(outPath.getParent());
				            	
				            try (
				            	OutputStream output = Files.newOutputStream(outPath)
				            ) {
				            	// KML files may require some translation, to workaround broken input files.
				            	if (fileName != null)
				            		copyKmlStream(zipStream, output);
				            	
				            	// Just copy the content directly, without translation.
				            	else
				            		IOUtils.copy(zipStream, output);
				            }
				       }
					}
					catch(IOException ex) {
						logger.error("Failed to unzip file", ex);
						uploadedDataLayer = false;
						FileUtils.deleteDirectory(kmzDir.toFile ());
			        }
				
					// Set the final file name of the data layer.
					fileName = subdir + "/" + fileName;
				}
				else if(uploadedDataLayer = docFilename.endsWith(".gpx")){
					fileName = doc.getFilename();
				}
				else if(uploadedDataLayer = docFilename.endsWith(".json")){
					fileName = doc.getFilename();
				}else if(uploadedDataLayer = docFilename.endsWith(".geojson")){
					fileName = doc.getFilename();
				}
				else if(uploadedDataLayer = docFilename.endsWith(".kml")){
					fileName = doc.getFilename();
				}
				
			} else
			{
				logger.error("Doc is null");
			}
			
			if (uploadedDataLayer) {

				datalayer.getDatalayersource().setLayername(fileName);
				
				String datalayerId = datalayerDao.insertDataLayer(dataSourceId, datalayer);

				if (orgId >= 0)
				{
					datalayerDao.insertDatalayerOrg(datalayerId, orgId);
				}

				if (collabroomId >= 0)
				{
					addCollabroomDatalayer(collabroomId, datalayerId, username);
				}
				else {

					//Rootfolder folder = folderDao.getRootFolder("Data", workspaceId);
					//int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folder.getFolderid());
					Folder folder = folderDao.getFolderByName("Upload", workspaceId);
					int nextFolderIndex = datalayerDao.getNextDatalayerFolderIndex(folder.getFolderid());
					datalayerDao.insertDataLayerFolder(folder.getFolderid(), datalayerId, nextFolderIndex);
					newDatalayerFolder = datalayerDao.getDatalayerfolder(datalayerId, folder.getFolderid());
				}

				datalayerResponse.setDatalayerfolders(Arrays.asList(newDatalayerFolder));
				datalayerResponse.setMessage("ok");
				datalayerResponse.setSuccess(true);
				response = Response.ok(datalayerResponse).status(Status.OK).build();
				
			}
			else{
				datalayerResponse.setSuccess(false);
				datalayerResponse.setMessage("Failed to Upload file.");
				response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
		}
		catch(Exception e) {
			logger.error("Failed to insert data layer", e);
			datalayerResponse.setSuccess(false);
			datalayerResponse.setMessage("Failed to add data layer.");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		if (Status.OK.getStatusCode() == response.getStatus()) {
			try {
				notifyNewChange(newDatalayerFolder, workspaceId);
			} catch (IOException e) {
				logger.error("Failed to publish DatalayerService message event", e);
			}
		}
		
		return response;
	}
	
	public Response getToken(String url, String username, String password){
		return Response.ok(this.requestToken(url, username, password)).status(Status.OK).build();
	}
	
	public Response getToken(String datasourceId){
		List<Map<String, Object>> data = datalayerDao.getAuthentication(datasourceId);
		
		if(data.get(0) != null){
			String internalUrl = (String) data.get(0).get(SADisplayConstants.INTERNAL_URL);
			String token = this.requestToken(internalUrl, 
					(String) data.get(0).get(SADisplayConstants.USER_NAME),
					(String) data.get(0).get(SADisplayConstants.PASSWORD)
			);
			if(token != null){
				return Response.ok(token).status(Status.OK).build();
			}
		}

		return Response.ok().status(Status.INTERNAL_SERVER_ERROR).build();
	}

	public Response addCollabroomDatalayer(int collabroomId, String datalayerId, String username)
	{
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		Response response = null;
		DatalayerCollabroom datalayerCollabroom = null;
		User user = null;
		int userid;

		try {

			user = userDao.getUser(username);
			userid = user.getUserId();
			
			datalayerCollabroom = datalayerDao.insertCollabRoomDatalayer(collabroomId, datalayerId, userid);
			notifyNewCollabroom(datalayerCollabroom);

			datalayerResponse.setMessage("ok");
			response = Response.ok(datalayerResponse).status(Status.OK).build();
		}
		catch (Exception ex) {
			logger.error("Failed to add collabroom datalayer", ex);
			datalayerResponse.setMessage("failed");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}

		return response;
	}
	
	public Response deleteCollabroomDataLayer(ArrayList<DatalayerCollabroom> datalayerCollabrooms){
		DatalayerServiceResponse datalayerResponse = new DatalayerServiceResponse();
		Response response = null;
		boolean deletedCollabroomDatalayer = false;

		try {
			
			deletedCollabroomDatalayer = datalayerDao.deleteCollabRoomDatalayers(datalayerCollabrooms);

			if(deletedCollabroomDatalayer){
				notifyDeleteCollabroom(datalayerCollabrooms);
				datalayerResponse.setCount(1);
				datalayerResponse.setMessage("ok");
				response = Response.ok(datalayerResponse).status(Status.OK).build();	
			}
			else{
				datalayerResponse.setMessage("Failed to delete collabroomdatalayer");
				response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
			}
			
		}
		catch (Exception ex) {
			logger.error("Failed to add collabroom datalayer", ex);
			datalayerResponse.setMessage("failed");
			response = Response.ok(datalayerResponse).status(Status.INTERNAL_SERVER_ERROR).build();
		}
		
		
		return response;
	}
	
	private String requestToken(String internalUrl, String username, String password){
		int index = internalUrl.indexOf("rest/services");
		if(index == -1){ 
			index = internalUrl.indexOf("services"); 
		}
		
		if(index > -1){
			StringBuffer url = new StringBuffer(internalUrl.substring(0, index));
			url.append("tokens/generateToken?");
			url.append("username=");
			url.append(username);
			url.append("&password=");
			url.append(password);
			url.append("&f=json");
			
			WebTarget target = jerseyClient.target(url.toString());
			Builder builder = target.request("json");
			return builder.get().readEntity(String.class);
		}
		
		return null;
	}
		
	
	private byte[] writeAttachmentWithDigest(Attachment attachment, Path path, String digestAlgorithm) throws IOException, NoSuchAlgorithmException {
		try(
			InputStream is = attachment.getDataHandler().getInputStream();
		) {
			MessageDigest md = MessageDigest.getInstance(digestAlgorithm);
			
			String ext = getFileExtension(attachment);
			if ("kml".equalsIgnoreCase(ext)) {
				try(
					OutputStream os = Files.newOutputStream(path);
					DigestOutputStream dos = new DigestOutputStream(os, md)
				) {
					copyKmlStream(is, dos);
				}
			}
			
			else {
				try (
					DigestInputStream dis = new DigestInputStream(is, md)
				) {
					Files.copy(dis, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			
			return md.digest();	
		}
	}

	
	private Document getDocument(Attachment attachment, Path directory) {
		
		Path path = this.createFile(attachment, directory.toString());
		
		Document doc = new Document();
		doc.setDisplayname(attachment.getContentDisposition().getParameter("filename"));
		doc.setFilename(path.getFileName().toString());
		doc.setFiletype(attachment.getContentType().toString());
		doc.setCreated(new Date());
		return doc;
	}


	/*
	private Path createFile(Attachment attachment, Path directory){	
		Path tempPath = null, path = null;
		try {
			Files.createDirectories(directory);

			//tempPath = Files.createTempFile(directory, null, null); 

			File tempFile;

			try {
				
				tempFile = directory.toFile();
				logger.error("createFile Path: " + directory.toFile().getAbsolutePath());

			} catch (Exception e)
			{
				logger.error("Exception in createFile.  Failed to convert path to file", e);
				return null;
			}

			try {


				tempFile = File.createTempFile(null, null, directory.toFile());
				logger.error("Created File: " + tempFile.getAbsolutePath());

				tempPath = tempFile.toPath();
				logger.error("Converted to Path: " + tempPath.toString());

			} catch (Exception e)
			{
				logger.error("Exception in createFile.  Failed to create temp file", e);
				return null;
			}


			byte[] digest = writeAttachmentWithDigest(attachment, tempPath, "MD5");
			
			String filename = new BigInteger(1, digest).toString();
			String ext = getFileExtension(attachment);
			if (ext != null) {
				filename += "." + ext;
			}
			path = directory.resolve(filename);
			path = Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
		
			// **** Does not work for Windows ****
			// Set proper file permissions on this file.
			/*Files.setPosixFilePermissions(path, EnumSet.of(
					PosixFilePermission.OWNER_READ,
					PosixFilePermission.OWNER_WRITE,
					PosixFilePermission.GROUP_READ,
					PosixFilePermission.GROUP_WRITE,
					PosixFilePermission.OTHERS_READ
				));*//*
		} catch (IOException|NoSuchAlgorithmException e) {
			logger.error("Failed to save file attachment", e);
			return null;
		} finally {
			//cleanup files
			if (tempPath != null) {
				File file = tempPath.toFile();
				if (file.exists()) {
					file.delete();
				}
			}
		}
		return path;
	} */


	private Path createFile(Attachment attachment, String directory){	
		Path path = null, tempPath = null;
		File tempFile = null, dir = null;
		String filename = null;

		try {

			try {

				logger.error("PATH: " + directory);
				logger.error("Temp PATH: " + tempDir);

				// Making temp file 
				tempFile = File.createTempFile("img_", null);
				tempPath = tempFile.toPath();

				// Creating directory for upload
				dir = new File(directory);
				dir.mkdirs();
				
				/*tempFile.setExecutable(1,1);
				tempFile.setReadable(1,1);
				tempFile.setWritable(1,1);*/

				logger.error("Created File: " + tempFile.getAbsolutePath());
			} catch (Exception e)
			{
				logger.error("Exception in createFile.  Failed to create temp file", e);
				return null;
			}


			byte[] digest = writeAttachmentWithDigest(attachment, tempPath, "MD5");
			
			filename = new BigInteger(1, digest).toString();
			String ext = getFileExtension(attachment);
			if (ext != null) {
				filename += "." + ext;
			}

			path = dir.toPath();
			path = path.resolve(filename);
			path = Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
		
			// **** Does not work for Windows ****
			// Set proper file permissions on this file.
			/*Files.setPosixFilePermissions(path, EnumSet.of(
					PosixFilePermission.OWNER_READ,
					PosixFilePermission.OWNER_WRITE,
					PosixFilePermission.GROUP_READ,
					PosixFilePermission.GROUP_WRITE,
					PosixFilePermission.OTHERS_READ
				));*/
		} catch (IOException|NoSuchAlgorithmException e) {
			logger.error("Failed to save file attachment", e);
			return null;
		} finally {
			//cleanup files
			if (tempPath != null) {
				File file = tempPath.toFile();
				if (file.exists()) {
					file.delete();
				}
			}
		}
		return path;
	}

	/** Utility method for copying (and possibly translating) a KML input stream to an output stream. */
	public void copyKmlStream(InputStream input, OutputStream output)
			throws IOException
	{
		byte[] buffer = new byte[4096];
		int n;

		// Convert the first (maximum of) 4096 bytes to a string.
		if (-1 == (n = input.read(buffer)))
			return;
		String prologue = new String(buffer, 0, n, "UTF-8");
	
		// Attempt to repair the document prologue, if a root <kml> tag is missing.
		Matcher matcher = MALFORMED_KML_PATTERN.matcher(prologue);
		if (matcher.find ()) {
			int insertionPoint = matcher.end() - 10; // Insertion point, before <Document> tag.
	
			IOUtils.write(prologue.substring(0, insertionPoint), output);
			IOUtils.write(KML_ROOT_START_TAG, output);
			IOUtils.write(prologue.substring(insertionPoint), output);
		}
	
		// Otherwise, simply write out the byte buffer and signal that no epilogue is needed.
		else {
			output.write(buffer, 0, n);
			prologue = null;
		}
	
		// Write out the rest of the stream.
		IOUtils.copy(input, output);
	
		// If an epilogue is needed, write it now.
		if (prologue != null)
			IOUtils.write("</kml>", output);
	}
	
	private String getMapserverDatasourceId() {
		if(mapserverURL == null) {
			return null;
		}
		String wmsMapserverURL = mapserverURL.concat("/wms");
		
		String datasourceId = datalayerDao.getDatasourceId(wmsMapserverURL);
		if (datasourceId == null) {
			int datasourcetypeid = datalayerDao.getDatasourceTypeId("wms");
			if (datasourcetypeid != -1) {
				Datasource ds = new Datasource();
				ds.setInternalurl(wmsMapserverURL);
				ds.setDatasourcetypeid(datasourcetypeid);
				ds.setDisplayname("NICS WMS Server");
				datasourceId = datalayerDao.insertDataSource(ds);
			}
		}
		return datasourceId;
	}
	
	private String getFileDatasourceId(String fileExt) {
		if(webserverURL == null) {
			return null;
		}
		String webServerURL = webserverURL.concat("/" + fileExt + "/");
		
		String datasourceId = datalayerDao.getDatasourceId(webServerURL);
		if (datasourceId == null) {
			int datasourcetypeid = datalayerDao.getDatasourceTypeId(fileExt);
			if (datasourcetypeid != -1) {
				Datasource ds = new Datasource();
				ds.setInternalurl(webServerURL);
				ds.setDatasourcetypeid(datasourcetypeid);
				datasourceId = datalayerDao.insertDataSource(ds);
			}
		}
		return datasourceId;
	}
	
	private GeoServer getGeoServer(Configuration config) {
		String geoserverUrl = config.getString(APIConfig.EXPORT_MAPSERVER_URL);
		if (geoserverUrl == null) {
			logger.error("API configuration error " + APIConfig.EXPORT_MAPSERVER_URL);
		}
		
		String geoserverUsername = config.getString(APIConfig.EXPORT_MAPSERVER_USERNAME);
		if (geoserverUsername == null) {
			logger.error("API configuration error " + APIConfig.EXPORT_MAPSERVER_USERNAME);
		}
		
		String geoserverPassword = config.getString(APIConfig.EXPORT_MAPSERVER_PASSWORD);
		if (geoserverPassword == null) {
			logger.error("API configuration error " + APIConfig.EXPORT_MAPSERVER_PASSWORD);
		}
		
		return new GeoServer(geoserverUrl.concat(APIConfig.EXPORT_REST_URL), geoserverUsername, geoserverPassword);
	}
	
	private String getFileExtension(Attachment attachment) {
		String filename = attachment.getContentDisposition().getParameter("filename");
		
		int idx = filename.lastIndexOf(".");
		if (idx != -1) {
			return filename.substring(idx + 1);
		}
		return null;
	}
	
	private void notifyNewChange(Datalayerfolder datalayerfolder, int workspaceId) throws IOException {
		if (datalayerfolder != null) {
			String topic = String.format("iweb.NICS.%s.datalayer.new", workspaceId);
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(datalayerfolder);
			getRabbitProducer().produce(topic, message);
		}
	}
	
	private void notifyDeleteChange(String dataSourceId) throws IOException {
		if (dataSourceId != null) {
			String topic = String.format("iweb.NICS.datalayer.delete");
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(dataSourceId);
			getRabbitProducer().produce(topic, message);
		}
	}
	
	private void notifyUpdateChange(Datalayer datalayer) throws IOException {
		if (datalayer != null) {
			String topic = String.format("iweb.NICS.datalayer.update");
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(datalayer);
			getRabbitProducer().produce(topic, message);
		}
	}

	private void notifyNewCollabroom(DatalayerCollabroom datalayerCollabroom) throws IOException {
		if (datalayerCollabroom != null) {
			String topic = String.format("iweb.NICS.collabroom.%d.datalayer.new", datalayerCollabroom.getCollabroomid());
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(datalayerCollabroom);
			getRabbitProducer().produce(topic, message);
		}
	}
	
	private void notifyDeleteCollabroom(ArrayList<DatalayerCollabroom> datalayerCollabrooms) throws IOException {
		if (datalayerCollabrooms != null) {
			String topic = String.format("iweb.NICS.collabroom.%d.datalayer.delete", datalayerCollabrooms.get(0).getCollabroomid());
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(datalayerCollabrooms);
			getRabbitProducer().produce(topic, message);
		}
	}
	
	private RabbitPubSubProducer getRabbitProducer() throws IOException {
		if (rabbitProducer == null) {
			rabbitProducer = RabbitFactory.makeRabbitPubSubProducer(
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_HOSTNAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_EXCHANGENAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERNAME_KEY),
					APIConfig.getInstance().getConfiguration().getString(APIConfig.RABBIT_USERPWD_KEY));
		}
		return rabbitProducer;
	}
	
	private Response getInvalidResponse(){
		return Response.status(Status.BAD_REQUEST).entity(
				Status.FORBIDDEN.getReasonPhrase()).build();
	}
}
