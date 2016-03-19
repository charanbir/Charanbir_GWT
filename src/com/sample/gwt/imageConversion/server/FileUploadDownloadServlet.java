package com.sample.gwt.imageConversion.server;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.util.UIDUtils;

public class FileUploadDownloadServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	Logger log = Logger.getLogger(FileUploadDownloadServlet.class.getName());
	File pngUploadedFile;

	private static final String TMP_DIR = "/tmp";

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {

		// get tiff
		File downloadTIFFFile = convertPNGtoTIFF(pngUploadedFile);

		// get dicom
		File jpegFile = convertUploadedFileToJPEG(pngUploadedFile);
		File dcmFile = convertJPEGtoDICOM(jpegFile);

		response.setContentType("application/zip");
		response.setStatus(HttpServletResponse.SC_OK);
		response.addHeader(
				"Content-Disposition",
				"attachment; filename=\""
						+ downloadTIFFFile.getName().substring(0,
								downloadTIFFFile.getName().length() - 4)
						+ ".zip\"");

		Collection<File> allFiles = new ArrayList<File>();
		allFiles.add(downloadTIFFFile);
		allFiles.add(dcmFile);

		OutputStream outputStream = null;
		BufferedOutputStream bufferedOutputStream = null;

		ZipOutputStream zipOutputStream = null;

		try {
			outputStream = response.getOutputStream();
			bufferedOutputStream = new BufferedOutputStream(outputStream);

			zipOutputStream = new ZipOutputStream(bufferedOutputStream);
			zipOutputStream.setLevel(ZipOutputStream.STORED);

			sendMultipleFiles(zipOutputStream, allFiles);
		} catch (IOException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} finally {
			if (zipOutputStream != null) {
				zipOutputStream.finish();
				zipOutputStream.flush();
				IOUtils.closeQuietly(zipOutputStream);
			}
			IOUtils.closeQuietly(bufferedOutputStream);
			IOUtils.closeQuietly(outputStream);
		}
	}

	private File convertUploadedFileToJPEG(File downloadTIFFFile) {
		BufferedImage bufferedImage;

		try {

			bufferedImage = ImageIO.read(downloadTIFFFile);
			BufferedImage newBufferedImage = new BufferedImage(
					bufferedImage.getWidth(), bufferedImage.getHeight(),
					BufferedImage.TYPE_INT_RGB);
			newBufferedImage.createGraphics().drawImage(bufferedImage, 0, 0,
					Color.WHITE, null);

			File jpegFile = new File(downloadTIFFFile.getName().substring(0,
					downloadTIFFFile.getName().length() - 4)
					+ ".jpg");
			ImageIO.write(newBufferedImage, "jpg", jpegFile);

			return jpegFile;

		} catch (IOException e) {

			e.printStackTrace();

		}
		return null;
	}

	private void sendMultipleFiles(ZipOutputStream zos,
			Collection<File> filesToSend) throws IOException {
		for (File f : filesToSend) {

			InputStream inStream = null;
			ZipEntry ze = null;

			try {
				inStream = new FileInputStream(f);
				ze = new ZipEntry(f.getName());
				zos.putNextEntry(ze);

				IOUtils.copy(inStream, zos);
			} catch (IOException e) {
				System.err
						.println("File(s) not found : " + f.getAbsolutePath());
			} finally {
				IOUtils.closeQuietly(inStream);
				if (ze != null) {
					zos.closeEntry();
				}
			}
		}
	}

	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {

		File tempDir = new File(".");
		if (!tempDir.exists()) {
			tempDir.mkdirs();
		}

		FileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload(factory);

		try {
			List<FileItem> items = upload.parseRequest(request);
			for (FileItem fileItem : items) {
				if (fileItem.isFormField())
					continue;

				String fileName = fileItem.getName();
				// get only the file name not whole path
				if (fileName != null) {
					fileName = FilenameUtils.getName(fileName);
				}

				pngUploadedFile = new File(TMP_DIR, fileName);
				fileItem.write(pngUploadedFile);
				response.setStatus(HttpServletResponse.SC_CREATED);
				response.getWriter().print("File uploaded successfully.");
				response.flushBuffer();

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public File convertPNGtoTIFF(File pngFile) {
		try {
			BufferedImage bufferedImage = ImageIO.read(pngFile);
			File tiffFile = new File(pngFile.getName().substring(0,
					pngFile.getName().length() - 4)
					+ ".tif");
			ImageIO.write(bufferedImage, "png", tiffFile);
			return tiffFile;

		} catch (Exception e) {
			System.err.println("Failed to convert PNG to TIFF: "
					+ e.getStackTrace());
		}
		return null;
	}

	public File convertJPEGtoDICOM(File jpgFile) {

		File dcmDestination = new File(jpgFile.getName().substring(0,
				jpgFile.getName().length() - 4)
				+ ".dcm");

		try {
			BufferedImage tiffImage = ImageIO.read(jpgFile);
			if (tiffImage == null)
				throw new Exception("Invalid file.");
			int i = 0;
			int colorComponents = tiffImage.getColorModel()
					.getNumColorComponents();
			int bitsPerPixel = tiffImage.getColorModel().getPixelSize();
			int bitsAllocated = (bitsPerPixel / colorComponents);
			int samplesPerPixel = colorComponents;

			DicomObject dicom = new BasicDicomObject();
			dicom.putString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100");
			dicom.putString(Tag.PhotometricInterpretation, VR.CS,
					samplesPerPixel == 3 ? "YBR_FULL_422" : "MONOCHROME2");

			dicom.putInt(Tag.SamplesPerPixel, VR.US, samplesPerPixel);
			dicom.putInt(Tag.Rows, VR.US, tiffImage.getHeight());
			dicom.putInt(Tag.Columns, VR.US, tiffImage.getWidth());
			dicom.putInt(Tag.BitsAllocated, VR.US, bitsAllocated);
			dicom.putInt(Tag.BitsStored, VR.US, bitsAllocated);
			dicom.putInt(Tag.HighBit, VR.US, bitsAllocated - 1);
			dicom.putInt(Tag.PixelRepresentation, VR.US, 0);

			dicom.putDate(Tag.InstanceCreationDate, VR.DA, new Date());
			dicom.putDate(Tag.InstanceCreationTime, VR.TM, new Date());

			dicom.putString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
			dicom.putString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
			dicom.putString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
			dicom.putString(Tag.SOPClassUID, VR.UI,
					"1.2.840.10008.5.1.4.1.1.481.1");

			dicom.initFileMetaInformation(UID.JPEGBaseline1);
			System.out.println(i++ + "   " + dicom);

			FileOutputStream fos = new FileOutputStream(dcmDestination);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			DicomOutputStream dos = new DicomOutputStream(bos);
			dos.writeDicomFile(dicom);

			dos.writeHeader(Tag.PixelData, VR.OB, -1);

			dos.writeHeader(Tag.Item, null, 0);
			System.out.println(i++ + "   " + dos);

			/*
			 * According to Gunter from dcm4che team we have to take care that
			 * the pixel data fragment length containing the JPEG stream has an
			 * even length.
			 */
			int jpgLen = (int) jpgFile.length();
			dos.writeHeader(Tag.Item, null, (jpgLen + 1) & ~1);

			FileInputStream fis = new FileInputStream(jpgFile);
			BufferedInputStream bis = new BufferedInputStream(fis);
			DataInputStream dis = new DataInputStream(bis);

			byte[] buffer = new byte[65536];
			int b;
			while ((b = dis.read(buffer)) > 0) {
				dos.write(buffer, 0, b);
			}

			/*
			 * According to Gunter from dcm4che team we have to take care that
			 * the pixel data fragment length containing the JPEG stream has an
			 * even length. So if needed the line below pads JPEG stream with
			 * odd length with 0 byte.
			 */
			if ((jpgLen & 1) != 0)
				dos.write(0);
			dos.writeHeader(Tag.SequenceDelimitationItem, null, 0);
			dos.close();
		} catch (Exception e) {
			System.out.println("ERROR: " + e.getMessage());
		}

		return dcmDestination;
	}

}
