<?php
/**
 * DocShare Secure API Endpoint
 * Consumed by the DocShare Android App to fetch the secure list of PDF documents.
 */

// Allow cross-origin requests from the Android WebView/HttpClient
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");
header("Content-Type: application/json; charset=UTF-8");

$dbFile = __DIR__ . '/db.json';
$uploadsDir = __DIR__ . '/uploads';

// Ensure uploads directory exists
if (!is_dir($uploadsDir)) {
    mkdir($uploadsDir, 0755, true);
}

// Initialize empty DB if not exists
if (!file_exists($dbFile)) {
    file_put_contents($dbFile, json_encode([]));
}

$dbData = json_decode(file_get_contents($dbFile), true);
if (!is_array($dbData)) {
    $dbData = [];
}

// Formats file sizes nicely
function formatBytes($bytes, $precision = 2) {
    $units = array('B', 'KB', 'MB', 'GB', 'TB');
    $bytes = max($bytes, 0);
    $pow = floor(($bytes ? log($bytes) : 0) / log(1024));
    $pow = min($pow, count($units) - 1);
    $bytes /= pow(1024, $pow);
    return round($bytes, $precision) . ' ' . $units[$pow];
}

// 1. Scan uploads directory for actual PDF files
$physicalPdfs = [];
if (is_dir($uploadsDir)) {
    $files = scandir($uploadsDir);
    if (is_array($files)) {
        foreach ($files as $file) {
            if ($file !== '.' && $file !== '..' && strtolower(pathinfo($file, PATHINFO_EXTENSION)) === 'pdf') {
                $physicalPdfs[$file] = [
                    'name' => $file,
                    'size' => @filesize($uploadsDir . '/' . $file) ?: 0,
                    'mtime' => @filemtime($uploadsDir . '/' . $file) ?: time()
                ];
            }
        }
    }
}

// 2. Cross-reference existing db.json entries to retain them
$synchronizedDocs = [];
$existingNamesInDb = [];

foreach ($dbData as $doc) {
    $name = $doc['name'] ?? '';
    if (!empty($name) && isset($physicalPdfs[$name])) {
        $synchronizedDocs[] = $doc;
        $existingNamesInDb[$name] = true;
    }
}

// 3. Add any new physical files that aren't indexed in db.json yet
$dbUpdated = false;
foreach ($physicalPdfs as $name => $info) {
    if (!isset($existingNamesInDb[$name])) {
        $finalTitle = ucwords(str_replace(['_', '-'], ' ', pathinfo($name, PATHINFO_FILENAME)));
        $newDoc = [
            "id" => "sec_doc_" . md5($name) . "_" . $info['mtime'],
            "name" => $name,
            "title" => $finalTitle,
            "url" => $name, // Stored as relative filename for host portability
            "dateAdded" => date("Y-m-d H:i:s \U\T\C", $info['mtime']),
            "fileSize" => formatBytes($info['size'])
        ];
        $synchronizedDocs[] = $newDoc;
        $dbUpdated = true;
    }
}

// 4. Update db.json if there are changes (e.g. deleted files or new unindexed files)
if ($dbUpdated || count($dbData) !== count($synchronizedDocs)) {
    file_put_contents($dbFile, json_encode($synchronizedDocs, JSON_PRETTY_PRINT));
    $dbData = $synchronizedDocs;
}

// Dynamically determine the scheme and domain host to construct absolute file URLs
$protocol = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off' || $_SERVER['SERVER_PORT'] == 443) ? "https" : "http";
$host = $_SERVER['HTTP_HOST'];
$requestUri = $_SERVER['REQUEST_URI'];
$currentDir = str_replace('\\', '/', dirname($requestUri));
$currentDir = rtrim($currentDir, '/');

// Absolute base URL for uploaded files
$fileBaseUrl = "$protocol://$host$currentDir/uploads/";

// Prepare matching response format for PdfRepository in Android App
$responseList = [];
foreach ($dbData as $doc) {
    $fileUrl = $doc['url'];
    // If url is saved as a relative filename, convert to absolute url
    if (!preg_match("~^(?:f|ht)tps?://~i", $fileUrl)) {
        $fileUrl = $fileBaseUrl . rawurlencode($fileUrl);
    }
    
    $responseList[] = [
        "id" => $doc['id'],
        "name" => $doc['name'],
        "title" => $doc['title'],
        "url" => $fileUrl,
        "dateAdded" => $doc['dateAdded'],
        "fileSize" => $doc['fileSize'],
        "isDemo" => false
    ];
}

echo json_encode($responseList, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
