package com.exemple.pdfdeverrouilleur

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File

/**
 * Résultat d'une tentative de déverrouillage.
 */
sealed class Resultat {
    /** Le PDF a été déverrouillé et enregistré à l'emplacement [fichier]. */
    data class Succes(val fichier: File, val etaitChiffre: Boolean) : Resultat()

    /** Le PDF exige un mot de passe d'ouverture : il faut le demander à l'utilisateur. */
    data object MotDePasseRequis : Resultat()

    /** Le mot de passe fourni est incorrect. */
    data object MotDePasseIncorrect : Resultat()

    /** Une autre erreur est survenue. */
    data class Erreur(val message: String) : Resultat()
}

/**
 * Déverrouille un PDF : retire le chiffrement et toutes les restrictions
 * (impression, copie, modification), puis enregistre une copie libre.
 *
 * Tout se passe sur l'appareil. Aucune donnée n'est envoyée sur le réseau.
 *
 * Deux cas :
 *  - Restrictions seules (mot de passe propriétaire) : [motDePasse] vide suffit.
 *  - Mot de passe d'ouverture : il faut fournir le bon [motDePasse], sinon
 *    la fonction renvoie [Resultat.MotDePasseRequis] ou [Resultat.MotDePasseIncorrect].
 */
object Deverrouilleur {

    private var initialise = false

    private fun assurerInit(context: Context) {
        if (!initialise) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialise = true
        }
    }

    fun deverrouiller(
        context: Context,
        source: Uri,
        nomSortie: String,
        motDePasse: String = ""
    ): Resultat {
        assurerInit(context)

        val fichierSortie = File(context.cacheDir, nomSortie)

        return try {
            context.contentResolver.openInputStream(source).use { entree ->
                if (entree == null) {
                    return Resultat.Erreur("Impossible de lire le fichier sélectionné.")
                }
                PDDocument.load(entree, motDePasse).use { document ->
                    val etaitChiffre = document.isEncrypted
                    // Retire chiffrement + toutes les permissions restrictives.
                    document.setAllSecurityToBeRemoved(true)
                    document.save(fichierSortie)
                    Resultat.Succes(fichierSortie, etaitChiffre)
                }
            }
        } catch (e: InvalidPasswordException) {
            // PDFBox lève la même exception qu'il manque le mot de passe
            // ou qu'il soit faux. On distingue selon ce que l'utilisateur a fourni.
            if (motDePasse.isEmpty()) Resultat.MotDePasseRequis
            else Resultat.MotDePasseIncorrect
        } catch (e: Exception) {
            Resultat.Erreur(e.message ?: "Erreur inconnue lors du traitement du PDF.")
        }
    }
}
