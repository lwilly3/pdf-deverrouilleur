package com.exemple.pdfdeverrouilleur

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EcranPrincipal()
                }
            }
        }
    }
}

/** Les étapes successives de l'écran, pour garder l'interface simple. */
private enum class Etape { ACCUEIL, MOT_DE_PASSE, TRAITEMENT, TERMINE, ERREUR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranPrincipal() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var etape by remember { mutableStateOf(Etape.ACCUEIL) }
    var pdfSource by remember { mutableStateOf<Uri?>(null) }
    var nomFichier by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var motDePasseVisible by remember { mutableStateOf(false) }
    var motDePasseErreur by remember { mutableStateOf(false) }
    var fichierResultat by remember { mutableStateOf<File?>(null) }
    var messageErreur by remember { mutableStateOf("") }
    var afficherMentions by remember { mutableStateOf(false) }

    // Écran des mentions open source (crédits), affiché par-dessus le reste.
    if (afficherMentions) {
        EcranMentions(onRetour = { afficherMentions = false })
        return
    }

    // Fonction qui lance le déverrouillage sur un thread de fond.
    fun lancer(uri: Uri, mdp: String) {
        scope.launch {
            etape = Etape.TRAITEMENT
            motDePasseErreur = false
            val sortie = nomSortiePour(nomFichier)
            val resultat = withContext(Dispatchers.IO) {
                Deverrouilleur.deverrouiller(context, uri, sortie, mdp)
            }
            when (resultat) {
                is Resultat.Succes -> {
                    fichierResultat = resultat.fichier
                    etape = Etape.TERMINE
                }
                is Resultat.MotDePasseRequis -> {
                    etape = Etape.MOT_DE_PASSE
                }
                is Resultat.MotDePasseIncorrect -> {
                    motDePasseErreur = true
                    etape = Etape.MOT_DE_PASSE
                }
                is Resultat.Erreur -> {
                    messageErreur = resultat.message
                    etape = Etape.ERREUR
                }
            }
        }
    }

    // Sélecteur de fichier système : ne renvoie que des PDF.
    val selecteur = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pdfSource = uri
            nomFichier = nomLisible(context, uri)
            motDePasse = ""
            // Premier essai sans mot de passe : couvre le cas « restrictions seules ».
            lancer(uri, "")
        }
    }

    fun reinitialiser() {
        etape = Etape.ACCUEIL
        pdfSource = null
        nomFichier = ""
        motDePasse = ""
        motDePasseErreur = false
        fichierResultat = null
        messageErreur = ""
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PDF Déverrouilleur", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { afficherMentions = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "À propos et mentions")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (etape) {
                Etape.ACCUEIL -> ContenuAccueil { selecteur.launch(arrayOf("application/pdf")) }

                Etape.MOT_DE_PASSE -> ContenuMotDePasse(
                    nomFichier = nomFichier,
                    motDePasse = motDePasse,
                    onMotDePasseChange = { motDePasse = it; motDePasseErreur = false },
                    visible = motDePasseVisible,
                    onToggleVisible = { motDePasseVisible = !motDePasseVisible },
                    erreur = motDePasseErreur,
                    onValider = { pdfSource?.let { lancer(it, motDePasse) } },
                    onAnnuler = { reinitialiser() }
                )

                Etape.TRAITEMENT -> ContenuTraitement(nomFichier)

                Etape.TERMINE -> ContenuTermine(
                    nomFichier = nomFichier,
                    onPartager = { fichierResultat?.let { partager(context, it) } },
                    onRecommencer = { reinitialiser() }
                )

                Etape.ERREUR -> ContenuErreur(
                    message = messageErreur,
                    onRecommencer = { reinitialiser() }
                )
            }
        }
    }
}

@Composable
private fun Entete(icone: ImageVector, titre: String, sousTitre: String) {
    Icon(
        imageVector = icone,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Text(titre, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        sousTitre,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ContenuAccueil(onChoisir: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Entete(
        icone = Icons.Filled.LockOpen,
        titre = "Déverrouiller un PDF",
        sousTitre = "Choisissez un fichier PDF. L'application retire les restrictions " +
            "d'impression et de copie. Tout se passe sur votre appareil, rien n'est envoyé en ligne."
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onChoisir,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Choisir un PDF", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContenuMotDePasse(
    nomFichier: String,
    motDePasse: String,
    onMotDePasseChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    erreur: Boolean,
    onValider: () -> Unit,
    onAnnuler: () -> Unit
) {
    Spacer(Modifier.height(24.dp))
    Entete(
        icone = Icons.Filled.Lock,
        titre = "Mot de passe requis",
        sousTitre = "« $nomFichier » demande un mot de passe à l'ouverture. " +
            "Saisissez-le pour continuer."
    )
    OutlinedTextField(
        value = motDePasse,
        onValueChange = onMotDePasseChange,
        label = { Text("Mot de passe du PDF") },
        singleLine = true,
        isError = erreur,
        supportingText = if (erreur) {
            { Text("Mot de passe incorrect. Réessayez.") }
        } else null,
        visualTransformation = if (visible) VisualTransformation.None
            else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions.Default,
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Masquer" else "Afficher"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = onValider,
        enabled = motDePasse.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("Déverrouiller")
    }
    TextButton(onClick = onAnnuler) {
        Text("Annuler")
    }
}

@Composable
private fun ContenuTraitement(nomFichier: String) {
    Spacer(Modifier.height(48.dp))
    CircularProgressIndicator(modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(8.dp))
    Text("Traitement en cours…", style = MaterialTheme.typography.titleMedium)
    Text(
        nomFichier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ContenuTermine(
    nomFichier: String,
    onPartager: () -> Unit,
    onRecommencer: () -> Unit
) {
    Spacer(Modifier.height(24.dp))
    Entete(
        icone = Icons.Filled.CheckCircle,
        titre = "PDF déverrouillé",
        sousTitre = "Le fichier est prêt, sans mot de passe ni restrictions. " +
            "Vos collaborateurs pourront l'imprimer et le copier."
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onPartager,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Icon(Icons.Filled.Share, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Partager le PDF", style = MaterialTheme.typography.titleMedium)
    }
    OutlinedButton(
        onClick = onRecommencer,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("Déverrouiller un autre fichier")
    }
}

@Composable
private fun ContenuErreur(message: String, onRecommencer: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Entete(
        icone = Icons.Filled.ErrorOutline,
        titre = "Échec du traitement",
        sousTitre = message
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onRecommencer, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Réessayer")
    }
}

/* ----- Écran des mentions open source (crédits) ----- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcranMentions(onRetour: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("À propos et mentions") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("PDF Déverrouilleur", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(
                "Cette application retire le mot de passe et les restrictions de PDF " +
                    "que vous êtes en droit de modifier. Le traitement se fait entièrement " +
                    "sur l'appareil ; aucune donnée n'est envoyée en ligne.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Bibliothèques open source utilisées",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            BIBLIOTHEQUES.forEach { lib ->
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text("${lib.nom} ${lib.version}",
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(lib.copyright, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lib.licence, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Notice", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            BlocTexte(NOTICE)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Licence Apache 2.0 (texte intégral)",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            BlocTexte(APACHE_2_0)

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Affiche un texte long dans un cadre à fond atténué, en police à chasse fixe. */
@Composable
private fun BlocTexte(texte: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            texte,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            modifier = Modifier.padding(12.dp)
        )
    }
}

/* ----- Utilitaires ----- */

/** Nom lisible du fichier choisi, pour l'affichage. */
private fun nomLisible(context: android.content.Context, uri: Uri): String {
    var nom = "document.pdf"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) {
            nom = c.getString(idx) ?: nom
        }
    }
    return nom
}

/** Construit le nom du fichier de sortie : « xxx_deverrouille.pdf ». */
private fun nomSortiePour(nomEntree: String): String {
    val base = nomEntree.removeSuffix(".pdf").removeSuffix(".PDF")
    return "${base}_deverrouille.pdf"
}

/** Ouvre la feuille de partage système avec le PDF déverrouillé. */
private fun partager(context: android.content.Context, fichier: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        fichier
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Partager le PDF déverrouillé"))
}
