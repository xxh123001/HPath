/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.commands.display;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * ImageServer that combines multiple single-channel images into a multi-channel composite.
 * Each input image becomes a separate channel in the output.
 * 
 * @author Custom Implementation
 * @since v0.6.0
 */
public class MultiChannelOverlayServer extends AbstractTileableImageServer {

    private static final Logger logger = LoggerFactory.getLogger(MultiChannelOverlayServer.class);

    private List<ImageServer<BufferedImage>> servers;
    private List<String> channelNames;
    private ImageServerMetadata originalMetadata;
    private boolean expandRGB; // Whether to expand RGB images to separate channels
    
    // Channel mapping: which server and which band (for RGB images)
    private static class ChannelMapping {
        final int serverIndex;
        final int bandIndex; // -1 for grayscale, 0=R, 1=G, 2=B for RGB
        
        ChannelMapping(int serverIndex, int bandIndex) {
            this.serverIndex = serverIndex;
            this.bandIndex = bandIndex;
        }
    }
    
    private List<ChannelMapping> channelMappings;
    
    /**
     * Create a multi-channel overlay server from multiple image servers.
     * 
     * @param servers list of image servers, each providing one channel
     * @param channelNames names for each channel
     * @param expandRGB whether to expand RGB images into separate R, G, B channels
     */
    public MultiChannelOverlayServer(List<ImageServer<BufferedImage>> servers, List<String> channelNames, boolean expandRGB) {
        super();
        
        if (servers == null || servers.isEmpty()) {
            throw new IllegalArgumentException("At least one server must be provided");
        }
        
        if (channelNames.size() != servers.size()) {
            throw new IllegalArgumentException("Number of channel names must match number of servers");
        }
        
        this.servers = new ArrayList<>(servers);
        this.channelNames = new ArrayList<>(channelNames);
        this.channelMappings = new ArrayList<>();
        this.expandRGB = expandRGB;
        
        // Build metadata based on first server
        ImageServer<BufferedImage> firstServer = servers.get(0);
        ImageServerMetadata metadata = firstServer.getMetadata();
        
        // Validate that all servers have the same dimensions
        int refWidth = metadata.getWidth();
        int refHeight = metadata.getHeight();
        logger.info("Reference image dimensions: {}x{}", refWidth, refHeight);
        
        for (int i = 1; i < servers.size(); i++) {
            ImageServerMetadata otherMetadata = servers.get(i).getMetadata();
            int otherWidth = otherMetadata.getWidth();
            int otherHeight = otherMetadata.getHeight();
            logger.info("Channel {} ({}) dimensions: {}x{}", i, channelNames.get(i), otherWidth, otherHeight);
            
            if (otherWidth != refWidth || otherHeight != refHeight) {
                String errorMsg = String.format(
                    "Image dimension mismatch! Reference: %dx%d, %s: %dx%d. All images must have the same dimensions.",
                    refWidth, refHeight, channelNames.get(i), otherWidth, otherHeight);
                logger.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
        }
        
        // Create channels based on input image types and expandRGB option
        List<ImageChannel> channels = new ArrayList<>();
        int channelColorIndex = 0;
        
        for (int i = 0; i < servers.size(); i++) {
            ImageServerMetadata serverMetadata = servers.get(i).getMetadata();
            boolean isRGB = serverMetadata.isRGB();
            
            if (isRGB && expandRGB) {
                // Expand RGB image to 3 separate channels
                logger.info("Image {} ({}) is RGB, expanding to R, G, B channels", i, channelNames.get(i));
                
                // Red channel
                channelMappings.add(new ChannelMapping(i, 0));
                Integer redColor = ColorTools.packRGB(255, 0, 0);
                channels.add(ImageChannel.getInstance(channelNames.get(i) + " (Red)", redColor));
                
                // Green channel
                channelMappings.add(new ChannelMapping(i, 1));
                Integer greenColor = ColorTools.packRGB(0, 255, 0);
                channels.add(ImageChannel.getInstance(channelNames.get(i) + " (Green)", greenColor));
                
                // Blue channel
                channelMappings.add(new ChannelMapping(i, 2));
                Integer blueColor = ColorTools.packRGB(0, 0, 255);
                channels.add(ImageChannel.getInstance(channelNames.get(i) + " (Blue)", blueColor));
                
                channelColorIndex += 3;
            } else {
                // Grayscale image or RGB kept as single channel
                if (isRGB) {
                    logger.info("Image {} ({}) is RGB, keeping as composite channel", i, channelNames.get(i));
                } else {
                    logger.info("Image {} ({}) is grayscale, adding as single channel", i, channelNames.get(i));
                }
                channelMappings.add(new ChannelMapping(i, -1));
                Integer channelColor = ImageChannel.getDefaultChannelColor(channelColorIndex);
                channels.add(ImageChannel.getInstance(channelNames.get(i), channelColor));
                channelColorIndex++;
            }
        }
        
        // Build new metadata with all channels
        var builder = new ImageServerMetadata.Builder(metadata)
                .channels(channels)
                .pixelType(PixelType.UINT16) // Use 16-bit to preserve dynamic range
                .rgb(false);
        
        this.originalMetadata = builder.build();
        
        logger.info("Created MultiChannelOverlayServer with {} total channels from {} images", 
            channels.size(), servers.size());
    }
    
    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return originalMetadata;
    }
    
    @Override
    protected ServerBuilder<BufferedImage> createServerBuilder() {
        return new MultiChannelOverlayServerBuilder(servers, channelNames, expandRGB);
    }
    
    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        RegionRequest request = tileRequest.getRegionRequest();
        int nChannels = channelMappings.size();  // Total output channels (including expanded RGB)
        
        logger.debug("Reading tile: region=[{},{},{}x{}], downsample={}, nChannels={}", 
            request.getX(), request.getY(), request.getWidth(), request.getHeight(), 
            request.getDownsample(), nChannels);
        
        // Read first server's image to determine actual output dimensions
        BufferedImage firstImg = null;
        int actualWidth = 0;
        int actualHeight = 0;
        
        ImageServer<BufferedImage> firstServer = servers.get(0);
        RegionRequest firstRequest = RegionRequest.createInstance(
                firstServer.getPath(),
                request.getDownsample(),
                request.getX(), request.getY(),
                request.getWidth(), request.getHeight(),
                request.getZ(), request.getT()
        );
        
        firstImg = firstServer.readRegion(firstRequest);
        if (firstImg == null) {
            logger.warn("First server returned null image for request: {}", firstRequest);
            return null;
        }
        
        actualWidth = firstImg.getWidth();
        actualHeight = firstImg.getHeight();
        
        logger.debug("Actual tile size from server: {}x{} (requested: {}x{}, downsample: {})", 
            actualWidth, actualHeight, request.getWidth(), request.getHeight(), request.getDownsample());
        
        // Create a raster with multiple bands based on ACTUAL returned size
        WritableRaster raster = WritableRaster.createBandedRaster(
            java.awt.image.DataBuffer.TYPE_USHORT,
            actualWidth, actualHeight, nChannels, null);
        
        // Cache loaded images to avoid re-reading the same server multiple times
        Map<Integer, BufferedImage> loadedImages = new java.util.HashMap<>();
        loadedImages.put(0, firstImg);
        
        // Process each output channel based on channel mappings
        for (int c = 0; c < nChannels; c++) {
            try {
                ChannelMapping mapping = channelMappings.get(c);
                int serverIndex = mapping.serverIndex;
                int bandIndex = mapping.bandIndex;
                
                // Load image from server if not already loaded
                BufferedImage sourceImg;
                if (loadedImages.containsKey(serverIndex)) {
                    sourceImg = loadedImages.get(serverIndex);
                } else {
                    ImageServer<BufferedImage> server = servers.get(serverIndex);
                    RegionRequest channelRequest = RegionRequest.createInstance(
                            server.getPath(),
                            request.getDownsample(),
                            request.getX(), request.getY(),
                            request.getWidth(), request.getHeight(),
                            request.getZ(), request.getT()
                    );
                    
                    sourceImg = server.readRegion(channelRequest);
                    if (sourceImg == null) {
                        logger.warn("Server {} returned null image for request: {}", serverIndex, channelRequest);
                        continue;
                    }
                    
                    // Verify dimensions
                    if (sourceImg.getWidth() != actualWidth || sourceImg.getHeight() != actualHeight) {
                        logger.warn("Server {} size mismatch! Expected: {}x{}, Got: {}x{}", 
                            serverIndex, actualWidth, actualHeight, sourceImg.getWidth(), sourceImg.getHeight());
                        continue;
                    }
                    
                    loadedImages.put(serverIndex, sourceImg);
                }
                
                // Extract pixel values for this channel
                int[] channelPixels = new int[actualWidth * actualHeight];
                if (bandIndex == -1) {
                    // Grayscale channel
                    extractSingleChannelData(sourceImg, channelPixels);
                } else {
                    // RGB channel - extract specific band (0=R, 1=G, 2=B)
                    extractRGBChannelData(sourceImg, channelPixels, bandIndex);
                }
                
                // Set the pixels for this specific band/channel
                raster.setSamples(0, 0, actualWidth, actualHeight, c, channelPixels);
                
            } catch (Exception e) {
                logger.error("Error processing channel " + c, e);
            }
        }
        
        // Create a ColorModel that matches the number of bands in the raster
        int[] bits = new int[nChannels];
        for (int i = 0; i < nChannels; i++) {
            bits[i] = 16; // 16 bits per channel
        }
        
        java.awt.color.ColorSpace cs = new java.awt.color.ColorSpace(java.awt.color.ColorSpace.TYPE_GRAY, nChannels) {
            @Override
            public float[] toRGB(float[] colorvalue) {
                // Simple pass-through for first channel
                return new float[]{colorvalue[0], colorvalue[0], colorvalue[0]};
            }
            
            @Override
            public float[] fromRGB(float[] rgbvalue) {
                float[] result = new float[nChannels];
                result[0] = rgbvalue[0]; // Use red channel
                return result;
            }
            
            @Override
            public float[] toCIEXYZ(float[] colorvalue) {
                return new float[]{colorvalue[0], colorvalue[0], colorvalue[0]};
            }
            
            @Override
            public float[] fromCIEXYZ(float[] colorvalue) {
                float[] result = new float[nChannels];
                result[0] = colorvalue[0];
                return result;
            }
        };
        
        java.awt.image.ComponentColorModel colorModel = new java.awt.image.ComponentColorModel(
            cs,
            bits,
            false,  // has alpha
            false,  // is alpha premultiplied
            java.awt.Transparency.OPAQUE,
            java.awt.image.DataBuffer.TYPE_USHORT
        );
        
        // Create the BufferedImage with the multi-band raster
        BufferedImage img = new BufferedImage(
            colorModel,
            raster,
            false,
            null);
        
        logger.debug("Created multi-channel tile: {}x{}, {} channels", 
            actualWidth, actualHeight, nChannels);
        
        return img;
    }
    
    /**
     * Extract pixel data from a single channel image (for grayscale images)
     * @param img the source image
     * @param output the output array (must be sized width*height)
     */
    private void extractSingleChannelData(BufferedImage img, int[] output) {
        int width = img.getWidth();
        int height = img.getHeight();
        
        // Validate output array size
        if (output.length != width * height) {
            logger.error("Output array size mismatch! Array: {}, Expected: {}x{}={}", 
                output.length, width, height, width * height);
            return;
        }
        
        if (img.getType() == BufferedImage.TYPE_BYTE_GRAY || 
            img.getType() == BufferedImage.TYPE_USHORT_GRAY) {
            // Grayscale image - direct extraction
            WritableRaster raster = img.getRaster();
            raster.getPixels(0, 0, width, height, output);
        } else {
            // RGB image - convert to grayscale using luminance
            int[] rgbPixels = new int[width * height];
            img.getRGB(0, 0, width, height, rgbPixels, 0, width);
            
            for (int i = 0; i < rgbPixels.length; i++) {
                int rgb = rgbPixels[i];
                // Calculate luminance: 0.299*R + 0.587*G + 0.114*B
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int luminance = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                // Scale to 16-bit
                output[i] = luminance * 257; // 257 = 65535 / 255
            }
        }
    }
    
    /**
     * Extract a specific RGB channel from an RGB image
     * @param img the source RGB image
     * @param output the output array (must be sized width*height)
     * @param channel which channel to extract (0=Red, 1=Green, 2=Blue)
     */
    private void extractRGBChannelData(BufferedImage img, int[] output, int channel) {
        int width = img.getWidth();
        int height = img.getHeight();
        
        // Validate output array size
        if (output.length != width * height) {
            logger.error("Output array size mismatch! Array: {}, Expected: {}x{}={}", 
                output.length, width, height, width * height);
            return;
        }
        
        // Get RGB pixels
        int[] rgbPixels = new int[width * height];
        img.getRGB(0, 0, width, height, rgbPixels, 0, width);
        
        // Extract the specific channel
        int shift = (2 - channel) * 8; // R=16, G=8, B=0
        for (int i = 0; i < rgbPixels.length; i++) {
            int val = (rgbPixels[i] >> shift) & 0xFF;
            // Scale to 16-bit
            output[i] = val * 257; // 257 = 65535 / 255
        }
    }
    
    @Override
    public Collection<URI> getURIs() {
        List<URI> uris = new ArrayList<>();
        for (ImageServer<BufferedImage> server : servers) {
            uris.addAll(server.getURIs());
        }
        return uris;
    }
    
    @Override
    public String getServerType() {
        return "Multi-channel overlay server";
    }
    
    @Override
    protected String createID() {
        StringBuilder sb = new StringBuilder("MultiChannelOverlay[");
        for (int i = 0; i < servers.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(servers.get(i).getPath());
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * ServerBuilder for MultiChannelOverlayServer
     */
    public static class MultiChannelOverlayServerBuilder implements ServerBuilder<BufferedImage> {
        
        private List<ImageServer<BufferedImage>> servers;
        private List<String> channelNames;
        private boolean expandRGB;
        
        public MultiChannelOverlayServerBuilder(List<ImageServer<BufferedImage>> servers, List<String> channelNames, boolean expandRGB) {
            this.servers = new ArrayList<>(servers);
            this.channelNames = new ArrayList<>(channelNames);
            this.expandRGB = expandRGB;
        }
        
        @Override
        public ImageServer<BufferedImage> build() throws Exception {
            return new MultiChannelOverlayServer(servers, channelNames, expandRGB);
        }
        
        @Override
        public Collection<URI> getURIs() {
            List<URI> uris = new ArrayList<>();
            for (ImageServer<BufferedImage> server : servers) {
                uris.addAll(server.getURIs());
            }
            return uris;
        }
        
        @Override
        public ServerBuilder<BufferedImage> updateURIs(Map<URI, URI> updateMap) {
            // For simplicity, return this instance
            // In a full implementation, we would update the server URIs
            return this;
        }
    }
}

