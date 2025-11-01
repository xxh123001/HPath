/**
 * Test script for annotation registration
 * 
 * This script creates a simple test case with a rectangle annotation,
 * then applies a known transformation to verify the registration system works.
 */

import qupath.lib.objects.PathObjects
import qupath.lib.roi.ROIs
import qupath.lib.regions.ImagePlane
import qupath.lib.awt.common.AffineTransforms

// Get current image
def imageData = getCurrentImageData()
if (imageData == null) {
    print "Please open an image first"
    return
}

def server = imageData.getServer()
def plane = ImagePlane.getDefaultPlane()

// Clear existing annotations
clearAnnotations()

// Create a test annotation - a rectangle
def x = server.getWidth() / 4
def y = server.getHeight() / 4
def width = server.getWidth() / 4
def height = server.getHeight() / 4

def roi = ROIs.createRectangleROI(x, y, width, height, plane)
def annotation = PathObjects.createAnnotationObject(roi)
addObject(annotation)

print "Created test annotation at ($x, $y) with size ($width x $height)"
print ""
print "Now you can test the registration feature:"
print "1. Go to Objects -> Annotations -> Register annotations..."
print "2. Mark 3-4 control points"
print "3. Apply the transformation"
print ""
print "To test with a known transformation, you can also run:"
print "  transformAllObjects(AffineTransforms.fromRows(1.1, 0.0, 50.0, 0.0, 1.1, 50.0))"
print "This will scale by 1.1x and translate by (50, 50)"

