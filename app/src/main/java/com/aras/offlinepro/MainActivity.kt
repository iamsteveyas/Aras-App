package com.aras.offlinepro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.*
import android.widget.Toast
import java.io.File

private val Bg = Color(0xFFF5F7FA)
private val Primary = Color(0xFF0B6E99)

class MainActivity : ComponentActivity() {
    private lateinit var store: SiteStore
    private lateinit var schema: FormSchema
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SiteStore(this)
        schema = FormSchema.load(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Primary, background = Bg, surface = Color.White)) { ArasApp(store, schema) }
        }
    }
}

@Composable
private fun ArasApp(store: SiteStore, schema: FormSchema) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("profile", 0) }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "").orEmpty()) }
    var screen by remember { mutableStateOf(if (userName.isBlank()) "profile" else "home") }
    var siteId by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxSize(), color = Bg) {
        when (screen) {
            "profile" -> ProfileScreen { name -> prefs.edit().putString("user_name", name.trim()).apply(); userName = name.trim(); screen = "home" }
            "home" -> HomeScreen(store.list(), { screen = "create" }) { id -> siteId = id; screen = "dashboard" }
            "create" -> CreateSiteScreen({ screen = "home" }, { code, name -> siteId = store.createSite(code, name); screen = "dashboard" }) { store.findByCode(it) != null }
            "dashboard" -> { val site = siteId?.let(store::get); if (site == null) screen = "home" else DashboardScreen(site, schema.categoryNames(), schema, { screen = "home" }, { selectedCategory = it; screen = "form" }) { screen = "photos" } }
            "form" -> { val site = siteId?.let(store::get); val cat = selectedCategory; if (site == null || cat == null) screen = "home" else FormScreen(site, schema.fields(cat), { screen = "dashboard" }) { label, value -> store.updateValue(site.id, label, value) } }
            "photos" -> { val site = siteId?.let(store::get); if (site == null) screen = "home" else PhotoScreen(site, store, schema) { screen = "dashboard" } }
        }
    }
}

@Composable
private fun ProfileScreen(onStart: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("ارس پرو", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text("ثبت اطلاعات سایت به صورت آفلاین")
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("نام و نام خانوادگی") }, singleLine = true)
        Spacer(Modifier.height(14.dp))
        Button(onClick = { if (name.isNotBlank()) onStart(name) }, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = name.isNotBlank(), shape = RoundedCornerShape(16.dp)) { Text("شروع استفاده از برنامه") }
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) Text("‹", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.clickable { onBack() }.padding(end = 10.dp))
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("ارس پرو", style = MaterialTheme.typography.labelMedium, color = Primary) }
    }
}

@Composable
private fun HomeScreen(sites: List<Site>, onNew: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("بازدیدهای سایت")
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("ایجاد سایت جدید") }
        Spacer(Modifier.height(18.dp)); Text("سایت‌های اخیر", Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sites) { site -> Card(Modifier.fillMaxWidth().clickable { onOpen(site.id) }, RoundedCornerShape(18.dp)) { Row(Modifier.padding(18.dp), Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(site.name, fontWeight = FontWeight.Bold); Text(site.code, color = Primary) }; Text("${site.photoCount} عکس") } } }
        }
    }
}

@Composable
private fun CreateSiteScreen(onBack: () -> Unit, onCreate: (String, String) -> Unit, exists: (String) -> Boolean) {
    var code by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
        Header("ایجاد سایت", onBack)
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("مشخصات اولیه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(code, { code = it; error = null }, Modifier.fillMaxWidth(), label = { Text("کد سایت") }, singleLine = true)
            OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth(), label = { Text("نام سایت") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { when { code.isBlank() || name.isBlank() -> error = "کد سایت و نام سایت الزامی هستند."; exists(code.trim()) -> error = "این کد سایت قبلاً ثبت شده است."; else -> onCreate(code.trim(), name.trim()) } }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("شروع ثبت اطلاعات") }
        }
    }
}

@Composable
private fun DashboardScreen(site: Site, categories: List<String>, schema: FormSchema, onBack: () -> Unit, onCategory: (String) -> Unit, onPhotos: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("${site.name} • ${site.code}", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) { Text("پیشرفت ثبت اطلاعات", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { progressFor(site, schema) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(6.dp)); Text("${site.values.size} فیلد ثبت شده • ${site.photoCount} عکس") } } }
            items(categories) { cat ->
                val fields = schema.fields(cat)
                Card(Modifier.fillMaxWidth().clickable { onCategory(cat) }, RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp)) { Text(if (cat == "Repeater") "رپیتر" else cat, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); fields.take(5).forEach { Text("• ${it.subCategory}") }; if (fields.size > 5) Text("+ ${fields.size - 5} زیرمجموعه دیگر", color = Primary) } }
            }
            item { OutlinedButton(onClick = { enqueueExport(LocalContext.current, site.id) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("ساخت خروجی ZIP") } }
            item { OutlinedButton(onClick = onPhotos, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("ثبت و مدیریت عکس‌ها") } }
        }
    }
}

private fun progressFor(site: Site, schema: FormSchema): Float { val total = schema.categoryNames().sumOf { schema.fields(it).size }.coerceAtLeast(1); return (site.values.size.toFloat() / total).coerceIn(0f, 1f) }

@Composable
private fun FormScreen(site: Site, fields: List<FieldDef>, onBack: () -> Unit, onSave: (String, String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header(fields.firstOrNull()?.category ?: "اطلاعات", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
            items(fields) { field -> Text(field.subCategory, style = MaterialTheme.typography.labelLarge, color = Primary, fontWeight = FontWeight.Bold); DynamicField(field, site.values[field.label].orEmpty(), onSave) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DynamicField(field: FieldDef, current: String, onSave: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (field.type == "combo") {
        ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
            OutlinedTextField(current, {}, Modifier.fillMaxWidth().menuAnchor(), readOnly = true, label = { Text("انتخاب ${field.subCategory}") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, supportingText = if (field.options.isEmpty()) ({ Text("مقدار این Combo در Excel ارائه نشده است.") }) else null)
            ExposedDropdownMenu(expanded, { expanded = false }) { field.options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSave(field.label, option); expanded = false }) } }
        }
    } else OutlinedTextField(current, { onSave(field.label, it) }, Modifier.fillMaxWidth(), label = { Text(field.subCategory) }, singleLine = true)
}

private fun enqueueExport(context: android.content.Context, siteId: String) {
    val request = OneTimeWorkRequestBuilder<ExportWorker>().setInputData(workDataOf("siteId" to siteId)).build()
    WorkManager.getInstance(context).enqueueUniqueWork("export_$siteId", ExistingWorkPolicy.REPLACE, request)
    Toast.makeText(context, "خروجی ZIP در پس‌زمینه ساخته می‌شود.", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoScreen(site: Site, store: SiteStore, schema: FormSchema, onBack: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(site.photoCount) }
    var pendingLaunch by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("سایر") }
    val labels = remember(schema) { schema.categoryNames().flatMap { schema.fields(it).map { f -> f.subCategory } }.distinct() + "سایر" }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) { count++; store.updatePhotoCount(site.id, count) } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingLaunch) { val dir = File(context.filesDir, "photos/${site.id}").apply { mkdirs() }; val file = File(dir, "${System.currentTimeMillis()}_${label.hashCode()}.jpg"); takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)) }
        pendingLaunch = false
    }
    Column(Modifier.fillMaxSize()) {
        Header("ثبت عکس", onBack)
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp)) { Text("سایت: ${site.name}", fontWeight = FontWeight.Bold); Text("کد: ${site.code}"); Text("تعداد عکس: $count") } }
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(label, {}, Modifier.fillMaxWidth().menuAnchor(), readOnly = true, label = { Text("برچسب عکس") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                ExposedDropdownMenu(expanded, { expanded = false }) { labels.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { label = item; expanded = false }) } }
            }
            Button(onClick = {
                pendingLaunch = true
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    val dir = File(context.filesDir, "photos/${site.id}").apply { mkdirs() }; val file = File(dir, "${System.currentTimeMillis()}_${label.hashCode()}.jpg"); takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)); pendingLaunch = false
                } else permissionLauncher.launch(Manifest.permission.CAMERA)
            }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("ثبت عکس") }
        }
    }
}
