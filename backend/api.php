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

// Initialize empty DB if not exists
if (!file_exists($dbFile)) {
    file_put_contents($dbFile, json_encode([]));
}

$dbData = json_decode(file_get_contents($dbFile), true);
if (!is_array($dbData)) {
    $dbData = [];
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
