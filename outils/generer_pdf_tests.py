#!/usr/bin/env python3
"""Génère des PDF de test avec diverses restrictions, pour l'app PDF Déverrouilleur.

Dépendance : pikepdf  (pip install pikepdf)
Les fichiers sont écrits dans ./pdf_tests/ à côté de l'exécution.
"""

from pathlib import Path
import pikepdf
from pikepdf import Pdf, Dictionary, Name, Permissions, Encryption

SORTIE = Path("pdf_tests")
SORTIE.mkdir(parents=True, exist_ok=True)


def pdf_base(titre: str, ligne2: str) -> Pdf:
    """Crée un PDF d'une page affichant deux lignes de texte."""
    def esc(s: str) -> str:
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    contenu = (
        f"BT /F1 20 Tf 72 720 Td ({esc(titre)}) Tj ET\n"
        f"BT /F1 12 Tf 72 690 Td ({esc(ligne2)}) Tj ET\n"
        f"BT /F1 12 Tf 72 660 Td (Fichier de test - PDF Deverrouilleur) Tj ET\n"
    ).encode("latin-1")
    pdf = Pdf.new()
    police = pdf.make_indirect(Dictionary(
        Type=Name.Font, Subtype=Name.Type1, BaseFont=Name.Helvetica))
    flux = pdf.make_stream(contenu)
    page = pdf.make_indirect(Dictionary(
        Type=Name.Page, MediaBox=[0, 0, 612, 792], Contents=flux,
        Resources=Dictionary(Font=Dictionary(F1=police))))
    pdf.pages.append(pikepdf.Page(page))
    return pdf


def enregistrer(pdf, nom, **kwargs):
    pdf.save(str(SORTIE / nom), **kwargs)
    print(f"  cree : {nom}")


perm_tout_interdit = Permissions(
    accessibility=True, extract=False, modify_annotation=False,
    modify_assembly=False, modify_form=False, modify_other=False,
    print_lowres=False, print_highres=False)
perm_impression_interdite = Permissions(extract=True, print_lowres=False, print_highres=False)
perm_copie_interdite = Permissions(extract=False, print_lowres=True, print_highres=True)

print(f"Dossier de sortie : {SORTIE.resolve()}")

enregistrer(pdf_base("1. Aucune protection", "Fichier libre."),
            "01_aucune_protection.pdf")
enregistrer(pdf_base("2. Impression interdite", "Mot de passe proprietaire."),
            "02_impression_interdite.pdf",
            encryption=Encryption(owner="proprio2", user="", R=4, allow=perm_impression_interdite))
enregistrer(pdf_base("3. Copie interdite", "Extraction de texte bloquee."),
            "03_copie_interdite.pdf",
            encryption=Encryption(owner="proprio3", user="", R=4, allow=perm_copie_interdite))
enregistrer(pdf_base("4. Toutes restrictions", "Toutes les restrictions actives."),
            "04_toutes_restrictions.pdf",
            encryption=Encryption(owner="proprio4", user="", R=4, allow=perm_tout_interdit))
enregistrer(pdf_base("5. Mot de passe ouverture", "Demande 'secret123' a l'ouverture."),
            "05_mot_de_passe_ouverture.pdf",
            encryption=Encryption(owner="proprio5", user="secret123", R=6, allow=perm_tout_interdit))
enregistrer(pdf_base("6. Ouverture + proprietaire", "Ouverture 'open2024', AES-256."),
            "06_ouverture_et_proprietaire.pdf",
            encryption=Encryption(owner="adminXYZ", user="open2024", R=6, allow=perm_tout_interdit))

print("Termine.")
