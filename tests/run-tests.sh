#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf .test-classes
mkdir -p .test-classes

# Download a real org.json library
wget -q -O .test-classes/json-20231013.jar https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar

# Provide Java stub dependencies for Android to compile headless tests
cat << 'EOF' > .test-classes/Uri.java
package android.net;
public class Uri {
    private String url;
    public static Uri parse(String url) { Uri u = new Uri(); u.url = url; return u; }
    public String toString() { return url; }
}
EOF
cat << 'EOF' > .test-classes/FaceEngine.java
package tw.mediaorganizer;
public class FaceEngine {
    public static class FaceSample {}
}
EOF
cat << 'EOF' > .test-classes/Scanner.java
package tw.mediaorganizer;

public class Scanner {
    public static final class Item {
        public android.net.Uri uri;public String name,mime,path;public long size,duration;public boolean video;public java.io.File thumb;public Similarity.Signature[] signatures;public java.util.List<FaceEngine.FaceSample> faces=new java.util.ArrayList<>();public boolean removed;
    }
}
EOF
cat << 'EOF' > .test-classes/MainActivity.java
package tw.mediaorganizer;
public class MainActivity {
    public static class MoveRecord {public android.net.Uri uri;public String oldPath,oldName,newPath,newName;public Scanner.Item item;}
}
EOF

# Compile stubs and pure logic files
javac -d .test-classes .test-classes/Uri.java .test-classes/FaceEngine.java app/src/main/java/tw/mediaorganizer/Similarity.java

# Compile Scanner because it depends on Similarity
javac -cp .test-classes -d .test-classes .test-classes/Scanner.java .test-classes/MainActivity.java

javac -cp .test-classes -d .test-classes tests/SimilarityTest.java tests/ScanAndDeleteTest.java

# Compile OperationState and test with the real org.json jar
javac -cp ".test-classes:.test-classes/json-20231013.jar" -d .test-classes app/src/main/java/tw/mediaorganizer/OperationState.java app/src/main/java/tw/mediaorganizer/OperationController.java app/src/main/java/tw/mediaorganizer/OperationListener.java tests/FileOperationTest.java

javac -d .test-classes app/src/main/java/tw/mediaorganizer/PreviewNavigation.java tests/PreviewNavigationTest.java
java -cp .test-classes tw.mediaorganizer.PreviewNavigationTest
javac -d .test-classes app/src/main/java/tw/mediaorganizer/VideoSwipeSeek.java tests/VideoSwipeSeekTest.java
java -cp .test-classes tw.mediaorganizer.VideoSwipeSeekTest
javac -d .test-classes app/src/main/java/tw/mediaorganizer/PlayerGesturePolicy.java tests/PlayerGesturePolicyTest.java
java -cp .test-classes tw.mediaorganizer.PlayerGesturePolicyTest

# UI regression guard: keep timeline and center-tap playback but no bottom
# previous/play/next buttons in either orientation.
PREVIEW=app/src/main/java/tw/mediaorganizer/MediaPreviewDialog.java
if grep -Eq 'videoButton\\(|goVideo\\(' "$PREVIEW"; then
  echo "FAIL: Redundant video navigation/playback button was reintroduced"
  exit 1
fi
grep -q 'timeline=new SeekBar(activity)' "$PREVIEW"
grep -q 'toggleVideo();' "$PREVIEW"
echo "PASS: Timeline-only playback UI (no bottom transport button row)"
javac -d .test-classes app/src/main/java/tw/mediaorganizer/PlayerProgressStyle.java tests/PlayerProgressStyleTest.java
java -cp .test-classes tw.mediaorganizer.PlayerProgressStyleTest
javac -d .test-classes app/src/main/java/tw/mediaorganizer/DeleteVerification.java tests/DeleteVerificationTest.java
java -cp .test-classes tw.mediaorganizer.DeleteVerificationTest
javac -d .test-classes app/src/main/java/tw/mediaorganizer/MediaManageFlow.java tests/MediaManageFlowTest.java
java -cp .test-classes tw.mediaorganizer.MediaManageFlowTest
javac -d .test-classes app/src/main/java/tw/mediaorganizer/DeletedMediaRegistry.java tests/DeletedMediaRegistryTest.java
java -cp .test-classes tw.mediaorganizer.DeletedMediaRegistryTest
javac -d .test-classes app/src/main/java/tw/mediaorganizer/PreviewZoom.java tests/PreviewZoomTest.java
java -cp .test-classes tw.mediaorganizer.PreviewZoomTest
javac -d .test-classes app/src/main/java/tw/mediaorganizer/FastScrollMath.java tests/FastScrollMathTest.java
java -cp .test-classes tw.mediaorganizer.FastScrollMathTest

java -cp .test-classes tw.mediaorganizer.SimilarityTest
java -cp .test-classes tw.mediaorganizer.ScanAndDeleteTest
java -cp ".test-classes:.test-classes/json-20231013.jar" tw.mediaorganizer.FileOperationTest
