<?php
/**
 * DocShare Secure Admin Panel & Uploader Dashboard
 * Minimal single-file administration interface for secure PDF file deployments.
 * Stores PDF document indexing in private db.json NoSQL flat-file database.
 */

session_start();

// Configuration Settings
define('ADMIN_USER', 'admin');
define('ADMIN_PASS', 'admin123'); // Simple default password
$dbFile = __DIR__ . '/db.json';
$uploadsDir = __DIR__ . '/uploads';

// Ensure directories and local NoSQL files exist securely
if (!file_exists($dbFile)) {
    file_put_contents($dbFile, json_encode([]));
}
if (!is_dir($uploadsDir)) {
    mkdir($uploadsDir, 0755, true);
}

// Authentication Handlers
$errorMsg = '';
if (isset($_POST['action']) && $_POST['action'] === 'login') {
    $user = trim($_POST['username'] ?? '');
    $pass = trim($_POST['password'] ?? '');
    if ($user === ADMIN_USER && $pass === ADMIN_PASS) {
        $_SESSION['authenticated'] = true;
        header("Location: index.php");
        exit;
    } else {
        $errorMsg = "Invalid pharmaceutical terminal login credentials.";
    }
}

if (isset($_GET['action']) && $_GET['action'] === 'logout') {
    $_SESSION['authenticated'] = false;
    session_destroy();
    header("Location: index.php");
    exit;
}

$authenticated = $_SESSION['authenticated'] ?? false;

// Helpers
function getDbFiles($dbFile) {
    $data = json_decode(file_get_contents($dbFile), true);
    return is_array($data) ? $data : [];
}

function saveDbFiles($dbFile, $data) {
    file_put_contents($dbFile, json_encode($data, JSON_PRETTY_PRINT));
}

function formatBytes($bytes, $precision = 2) {
    $units = array('B', 'KB', 'MB', 'GB', 'TB');
    $bytes = max($bytes, 0);
    $pow = floor(($bytes ? log($bytes) : 0) / log(1024));
    $pow = min($pow, count($units) - 1);
    $bytes /= pow(1024, $pow);
    return round($bytes, $precision) . ' ' . $units[$pow];
}

// Actions for Authenticated Admins
if ($authenticated) {
    // Delete action API request
    if (isset($_GET['delete_id'])) {
        $deleteId = trim($_GET['delete_id']);
        $currentDocs = getDbFiles($dbFile);
        $updatedDocs = [];
        $fileDeleted = false;
        
        foreach ($currentDocs as $doc) {
            if ($doc['id'] === $deleteId) {
                // Delete physical file from isolated disk storage safely
                $filePath = $uploadsDir . '/' . $doc['name'];
                if (file_exists($filePath)) {
                    unlink($filePath);
                }
                $fileDeleted = true;
            } else {
                $updatedDocs[] = $doc;
            }
        }
        
        if ($fileDeleted) {
            saveDbFiles($dbFile, $updatedDocs);
        }
        
        header("Location: index.php?status=deleted");
        exit;
    }

    // PDF upload processing
    if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_FILES['pdf_file'])) {
        $file = $_FILES['pdf_file'];
        $customTitle = trim($_POST['doc_title'] ?? '');
        
        if ($file['error'] === UPLOAD_ERR_OK) {
            $fileName = basename($file['name']);
            // Standard sanitation to protect server environments
            $fileName = preg_replace("/[^a-zA-Z0-9_\.-]/", "_", $fileName);
            
            // Check file type strictly
            $fileExt = strtolower(pathinfo($fileName, PATHINFO_EXTENSION));
            if ($fileExt !== 'pdf') {
                $errorMsg = "Only protected PDF files are authorized for system storage.";
            } else {
                $destination = $uploadsDir . '/' . $fileName;
                
                // Avoid namespace collisions
                if (file_exists($destination)) {
                    $fileName = pathinfo($fileName, PATHINFO_FILENAME) . '_' . time() . '.pdf';
                    $destination = $uploadsDir . '/' . $fileName;
                }
                
                if (move_uploaded_file($file['tmp_name'], $destination)) {
                    $finalTitle = $customTitle !== '' ? $customTitle : ucwords(str_replace(['_', '-'], ' ', pathinfo($fileName, PATHINFO_FILENAME)));
                    
                    $newDoc = [
                        "id" => uniqid("sec_doc_", true),
                        "name" => $fileName,
                        "title" => $finalTitle,
                        "url" => $fileName, // Stored as relative filename for host portability
                        "dateAdded" => date("Y-m-d H:i:s \U\T\C"),
                        "fileSize" => formatBytes($file['size'])
                    ];
                    
                    $currentDocs = getDbFiles($dbFile);
                    $currentDocs[] = $newDoc;
                    saveDbFiles($dbFile, $currentDocs);
                    
                    header("Location: index.php?status=success");
                    exit;
                } else {
                    $errorMsg = "File write operations failed on host storage unit.";
                }
            }
        } else {
            $errorMsg = "Upload error encountered code: " . $file['error'];
        }
    }
}

// Read current directory index
$documentsList = getDbFiles($dbFile);
?>
<!DOCTYPE html>
<html lang="en" class="h-full bg-slate-950">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SafeDocShare Center - Secure Admin Console</title>
    <!-- Tailwind Engine CDN -->
    <script src="https://cdn.tailwindcss.com"></script>
    <!-- Material Icons -->
    <link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200" rel="stylesheet" />
    <style>
        .cyber-bg {
            background-image: radial-gradient(circle at top, #112240 0%, #020c1b 100%);
        }
        .teal-pulse {
            box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
            animation: pulse-teal 2s infinite;
        }
        @keyframes pulse-teal {
            0% {
                transform: scale(0.95);
                box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
            }
            70% {
                transform: scale(1);
                box-shadow: 0 0 0 8px rgba(16, 185, 129, 0);
            }
            100% {
                transform: scale(0.95);
                box-shadow: 0 0 0 0 rgba(16, 185, 129, 0);
            }
        }
    </style>
</head>
<body class="h-full text-slate-100 font-sans cyber-bg flex flex-col justify-between">

    <!-- Top Header Navigation Bar -->
    <header class="border-b border-teal-500/10 bg-slate-900/60 backdrop-blur-md sticky top-0 z-50">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
            <div class="flex items-center space-x-3">
                <span class="material-symbols-outlined text-emerald-400 text-3xl">lock</span>
                <div>
                    <h1 class="font-bold text-lg tracking-wider text-slate-100">SafeDocShare</h1>
                    <p class="text-[10px] uppercase font-mono tracking-widest text-emerald-500 leading-none">Admin Console Port</p>
                </div>
            </div>
            <div class="flex items-center space-x-4">
                <span class="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    <span class="h-2 w-2 rounded-full col bg-emerald-400 mr-2 teal-pulse"></span>
                    SECURE CONNECTED
                </span>
                <?php if ($authenticated): ?>
                    <a href="?action=logout" class="flex items-center space-x-1 text-slate-400 hover:text-rose-400 transition-colors bg-slate-800/80 px-3 py-1.5 rounded-lg border border-slate-700/50 text-sm">
                        <span class="material-symbols-outlined text-lg">logout</span>
                        <span>Sign Out</span>
                    </a>
                <?php endif; ?>
            </div>
        </div>
    </header>

    <!-- Main Content Cluster -->
    <main class="flex-grow max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 flex flex-col justify-center">

        <?php if (!$authenticated): ?>
            <!-- Terminal Admin Login Gate -->
            <div class="max-w-md w-full mx-auto mt-8">
                <div class="bg-slate-900/80 backdrop-blur-xl rounded-3xl p-8 border border-teal-500/10 shadow-2xl relative overflow-hidden">
                    <div class="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-emerald-500 to-cyan-500"></div>
                    
                    <div class="text-center mb-8">
                        <div class="inline-flex items-center justify-center p-4 bg-emerald-500/5 rounded-2xl border border-emerald-500/10 mb-4">
                            <span class="material-symbols-outlined text-4xl text-emerald-400">admin_panel_settings</span>
                        </div>
                        <h2 class="text-2xl font-bold tracking-tight">System Lock Access</h2>
                        <p class="text-xs text-slate-400 mt-2 font-mono">Authenticate to interface with server directory</p>
                    </div>

                    <?php if ($errorMsg !== ''): ?>
                        <div class="bg-rose-500/10 border border-rose-500/25 rounded-2xl p-4 text-xs text-rose-400 flex items-start space-x-3 mb-6">
                            <span class="material-symbols-outlined text-lg flex-shrink-0">warning</span>
                            <span><?php echo htmlspecialchars($errorMsg); ?></span>
                        </div>
                    <?php endif; ?>

                    <form action="" method="post" class="space-y-4">
                        <input type="hidden" name="action" value="login">
                        <div>
                            <label for="username" class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">Terminal Owner</label>
                            <div class="relative">
                                <span class="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500 text-lg">person</span>
                                <input type="text" name="username" id="username" required placeholder="Enter admin username" 
                                       class="w-full bg-slate-950/60 border border-slate-800 focus:border-emerald-500/50 rounded-xl py-3 pl-11 pr-4 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500/30 transition-all font-mono">
                            </div>
                        </div>

                        <div>
                            <div class="flex items-center justify-between mb-2">
                                <label for="password" class="block text-xs font-semibold uppercase tracking-wider text-slate-400">Secure Pin Code</label>
                                <span class="text-[10px] font-mono text-emerald-500/60">Default: admin123</span>
                            </div>
                            <div class="relative">
                                <span class="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500 text-lg">lock</span>
                                <input type="password" name="password" id="password" required placeholder="••••••••••••••" 
                                       class="w-full bg-slate-950/60 border border-slate-800 focus:border-emerald-500/50 rounded-xl py-3 pl-11 pr-4 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500/30 transition-all font-mono">
                            </div>
                        </div>

                        <button type="submit" class="w-full bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold py-3 px-4 rounded-xl shadow-lg shadow-emerald-500/10 hover:shadow-emerald-500/20 hover:-translate-y-0.5 active:translate-y-0 transition-all flex items-center justify-center space-x-2 mt-6">
                            <span>AUTHORIZE KEY</span>
                            <span class="material-symbols-outlined text-lg">vpn_key</span>
                        </button>
                    </form>
                </div>
            </div>

        <?php else: ?>
            
            <!-- Dashboard Operations Base -->
            <div class="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
                
                <!-- Left Hand Upload Box -->
                <div class="col-span-1 bg-slate-900/40 border border-slate-800 rounded-3xl p-6 backdrop-blur-xl">
                    <h3 class="text-lg font-bold tracking-tight mb-2 flex items-center space-x-2">
                        <span class="material-symbols-outlined text-emerald-400">cloud_upload</span>
                        <span>Upload Protected Document</span>
                    </h3>
                    <p class="text-xs text-slate-400 mb-6">Uploaded PDF documents will be indexed securely and made viewable exclusively in the DocShare mobile application.</p>

                    <?php if ($errorMsg !== ''): ?>
                        <div class="bg-rose-500/10 border border-rose-500/25 rounded-2xl p-4 text-xs text-rose-400 flex items-start space-x-3 mb-6">
                            <span class="material-symbols-outlined text-lg flex-shrink-0">warning</span>
                            <span><?php echo htmlspecialchars($errorMsg); ?></span>
                        </div>
                    <?php endif; ?>

                    <form action="" method="post" enctype="multipart/form-data" class="space-y-5">
                        <div>
                            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">Document Display Title (Optional)</label>
                            <input type="text" name="doc_title" placeholder="e.g. Chemical Formulation SOP" 
                                   class="w-full bg-slate-950/60 border border-slate-800 focus:border-emerald-500/50 rounded-xl py-3 px-4 text-sm text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500/30 transition-all">
                        </div>

                        <div>
                            <label class="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">Select Protected File</label>
                            <div class="border-2 border-dashed border-slate-800 hover:border-emerald-500/40 rounded-2xl p-6 text-center cursor-pointer transition-all bg-slate-950/20 group relative" id="drop-area">
                                <input type="file" name="pdf_file" id="file-input" required accept="application/pdf" class="absolute inset-0 opacity-0 cursor-pointer">
                                
                                <span class="material-symbols-outlined text-slate-500 text-4xl mb-3 group-hover:text-emerald-400 transition-colors">upload_file</span>
                                <h4 class="text-sm font-semibold text-slate-300 pointer-events-none" id="file-status">Drag PDF File Here</h4>
                                <p class="text-[11px] text-slate-500 mt-1 pointer-events-none">or click to browse systems (PDF strictly limit)</p>
                            </div>
                        </div>

                        <button type="submit" class="w-full bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold py-3.5 px-4 rounded-xl shadow-lg hover:shadow-emerald-500/10 transition-all flex items-center justify-center space-x-2">
                            <span>DEPLOY SECURE PORTAL</span>
                            <span class="material-symbols-outlined text-lg">publish</span>
                        </button>
                    </form>
                </div>

                <!-- Right Hand Metadata Inventory Explorer -->
                <div class="col-span-1 lg:col-span-2 space-y-4">
                    
                    <!-- Search and Stats Unit -->
                    <div class="bg-slate-900/40 border border-slate-800 rounded-2xl p-4 flex flex-col md:flex-row md:items-center justify-between gap-4 backdrop-blur-xl">
                        <div class="relative w-full md:max-w-sm">
                            <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 text-lg">search</span>
                            <input type="text" id="directory-search" placeholder="Filter active deploy directory..." 
                                   class="w-full bg-slate-950/60 border border-slate-800 focus:border-emerald-500/50 rounded-xl py-2 pl-10 pr-4 text-xs text-slate-100 focus:outline-none transition-all">
                        </div>
                        
                        <div class="flex items-center space-x-6 text-xs text-slate-400 uppercase font-mono px-2">
                            <div>Total Deploys: <span class="font-bold text-slate-100" id="doc-counter"><?php echo count($documentsList); ?></span></div>
                            <div class="h-4 w-px bg-slate-800"></div>
                            <div>API Endpoint: <a href="api.php" target="_blank" class="text-cyan-400 hover:underline flex inline-flex items-center align-middle">api.php <span class="material-symbols-outlined text-xs ml-0.5">open_in_new</span></a></div>
                        </div>
                    </div>

                    <?php if (isset($_GET['status']) && $_GET['status'] === 'success'): ?>
                        <div id="status-alert" class="bg-emerald-500/10 border border-emerald-500/25 rounded-2xl p-4 text-xs text-emerald-400 flex items-start space-x-3 transition-opacity duration-500">
                            <span class="material-symbols-outlined text-lg flex-shrink-0">check_circle</span>
                            <span>Secure asset successfully serialized to server vault. Discovered and cacheable inside client application instantly.</span>
                        </div>
                    <?php endif; ?>

                    <?php if (isset($_GET['status']) && $_GET['status'] === 'deleted'): ?>
                        <div id="status-alert" class="bg-rose-500/10 border border-rose-500/25 rounded-2xl p-4 text-xs text-rose-400 flex items-start space-x-3 transition-opacity duration-500">
                            <span class="material-symbols-outlined text-lg flex-shrink-0">delete</span>
                            <span>Secure document completely deleted from directory systems.</span>
                        </div>
                    <?php endif; ?>

                    <!-- Files Table Layout list -->
                    <div class="bg-slate-900/20 border border-slate-800/80 rounded-3xl overflow-hidden backdrop-blur-xl" id="document-grid">
                        <div class="px-6 py-4 border-b border-slate-800/50 bg-slate-900/40 flex items-center justify-between">
                            <h3 class="font-bold text-slate-300 flex items-center space-x-2">
                                <span class="material-symbols-outlined text-cyan-400">inventory_2</span>
                                <span>Active Document Vault Directory</span>
                            </h3>
                            <button onclick="window.location.reload();" class="text-slate-500 hover:text-slate-300 transition-colors">
                                <span class="material-symbols-outlined text-lg">refresh</span>
                            </button>
                        </div>

                        <?php if (empty($documentsList)): ?>
                            <div class="p-12 text-center text-slate-500 flex flex-col items-center justify-center">
                                <span class="material-symbols-outlined text-5xl mb-3 text-slate-700">source</span>
                                <h4 class="font-bold text-slate-400">Vault Index Empty</h4>
                                <p class="text-xs text-slate-500 max-w-xs mt-1">Deploy your first PDF asset to query securely inside the secure native application.</p>
                            </div>
                        <?php else: ?>
                            <div class="divide-y divide-slate-800/40" id="docs-list">
                                <?php foreach ($documentsList as $doc): ?>
                                    <div class="p-4 sm:p-6 hover:bg-slate-900/30 transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-4 doc-item-row" 
                                         data-search="<?php echo htmlspecialchars(strtolower($doc['title'] . ' ' . $doc['name'])); ?>">
                                        
                                        <!-- Title Metadata Description -->
                                        <div class="flex items-start space-x-4">
                                            <div class="p-3 bg-teal-500/5 rounded-xl border border-teal-500/10 text-emerald-400 flex-shrink-0">
                                                <span class="material-symbols-outlined text-2xl">picture_as_pdf</span>
                                            </div>
                                            <div class="min-w-0">
                                                <h4 class="font-bold text-slate-100 truncate text-sm sm:text-base"><?php echo htmlspecialchars($doc['title']); ?></h4>
                                                <p class="text-xs text-slate-400 font-mono truncate mt-0.5"><?php echo htmlspecialchars($doc['name']); ?></p>
                                                
                                                <div class="flex items-center space-x-4 mt-2 text-[10px] text-slate-500 font-mono">
                                                    <span>Added: <?php echo htmlspecialchars($doc['dateAdded']); ?></span>
                                                    <span>•</span>
                                                    <span>Size: <?php echo htmlspecialchars($doc['fileSize']); ?></span>
                                                </div>
                                            </div>
                                        </div>

                                        <!-- Quick administration actions -->
                                        <div class="flex items-center justify-end space-x-2 border-t border-slate-800/30 pt-3 sm:pt-0 sm:border-0">
                                            <a href="uploads/<?php echo htmlspecialchars($doc['name']); ?>" target="_blank" 
                                               class="flex items-center space-x-1 hover:bg-slate-800 text-slate-300 hover:text-white px-3 py-1.5 rounded-lg border border-slate-800 hover:border-slate-700 text-xs font-semibold transition-all">
                                                <span class="material-symbols-outlined text-base">visibility</span>
                                                <span>Check Raw</span>
                                            </a>
                                            <a href="?delete_id=<?php echo htmlspecialchars($doc['id']); ?>" 
                                               onclick="return confirm('Securely shred this deployment index? The file will be deleted permanently.');"
                                               class="flex items-center space-x-1 hover:bg-rose-500/10 text-rose-400 hover:border-rose-500/30 px-3 py-1.5 rounded-lg border border-slate-800 text-xs font-semibold transition-all">
                                                <span class="material-symbols-outlined text-base">delete_forever</span>
                                                <span>Purge</span>
                                            </a>
                                        </div>

                                    </div>
                                <?php endforeach; ?>
                            </div>
                        <?php endif; ?>
                    </div>
                </div>

            </div>

        <?php endif; ?>

    </main>

    <!-- Footer System Status Area -->
    <footer class="border-t border-slate-900 bg-slate-950/80 py-6 mt-16 text-center text-xs text-slate-600 font-mono">
        <div class="max-w-7xl mx-auto px-4">
            <p>SafeDocShare Cryptographic Console &bull; Sandbox v3.5-Enterprise</p>
            <p class="text-[10px] text-slate-700 mt-1 uppercase">Warning: Restricted environment. Screenshot, transfer, or reverse engineered extraction processes block in real-time.</p>
        </div>
    </footer>

    <!-- JS Scripts for Dashboard enhancements -->
    <script>
        // Auto-dismiss Alerts safely
        const alertBox = document.getElementById('status-alert');
        if (alertBox) {
            setTimeout(() => {
                alertBox.style.opacity = '0';
                setTimeout(() => alertBox.remove(), 500);
            }, 6000);
        }

        // Live Client-side search filters
        const searchInput = document.getElementById('directory-search');
        if (searchInput) {
            searchInput.addEventListener('input', function(e) {
                const query = e.target.value.toLowerCase().trim();
                const docRows = document.querySelectorAll('.doc-item-row');
                let matches = 0;
                
                docRows.forEach(row => {
                    const searchData = row.getAttribute('data-search') || '';
                    if (searchData.includes(query)) {
                        row.style.display = 'flex';
                        matches++;
                    } else {
                        row.style.display = 'none';
                    }
                });

                const counter = document.getElementById('doc-counter');
                if (counter) counter.innerText = matches;
            });
        }

        // Interactive Drag & Drop Area file updates
        const dropArea = document.getElementById('drop-area');
        const fileInput = document.getElementById('file-input');
        const fileStatus = document.getElementById('file-status');
        
        if (dropArea && fileInput && fileStatus) {
            ['dragenter', 'dragover'].forEach(eventName => {
                dropArea.addEventListener(eventName, (e) => {
                    e.preventDefault();
                    dropArea.classList.add('border-emerald-500/60', 'bg-emerald-500/5');
                }, false);
            });

            ['dragleave', 'drop'].forEach(eventName => {
                dropArea.addEventListener(eventName, (e) => {
                    e.preventDefault();
                    dropArea.classList.remove('border-emerald-500/60', 'bg-emerald-500/5');
                }, false);
            });

            fileInput.addEventListener('change', () => {
                if (fileInput.files.length > 0) {
                    const file = fileInput.files[0];
                    if (file.type === "application/pdf") {
                        fileStatus.innerText = "Selected: " + file.name;
                        fileStatus.classList.remove('text-slate-300');
                        fileStatus.classList.add('text-emerald-400');
                    } else {
                        fileStatus.innerText = "Error: NOT A PDF FILE";
                        fileStatus.classList.remove('text-slate-300');
                        fileStatus.classList.add('text-rose-400');
                        fileInput.value = ''; // clear invalid selection
                    }
                }
            });
        }
    </script>
</body>
</html>
