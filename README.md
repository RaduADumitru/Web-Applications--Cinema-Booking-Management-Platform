# Web-Applications--Cinema-Booking-Management-Platform
Aplicația reprezintă o platformă de tip Cinema Booking & Management System, care permite utilizatorilor să vizualizeze filme disponibile, să selecteze proiecții și să efectueze rezervări de bilete, iar administratorilor să gestioneze conținutul platformei (filme, săli, program).
Sistemul este proiectat inițial ca o aplicație monolitică, urmând ca ulterior să fie descompus într-o arhitectură bazată pe microservicii. Separarea se face pe baza responsabilităților principale ale aplicației: gestionarea utilizatorilor, gestionarea filmelor și gestionarea rezervărilor.

# Management utilizatori
Sistemul trebuie să permită înregistrarea utilizatorilor.
Sistemul trebuie să permită autentificarea utilizatorilor.
Sistemul trebuie să permită logout-ul utilizatorilor.
Sistemul trebuie să gestioneze roluri.
Sistemul trebuie să restricționeze accesul la anumite funcționalități pe baza rolului.

# Management filme
Sistemul trebuie să permită vizualizarea listei de filme.
Sistemul trebuie să permită vizualizarea detaliilor unui film.
Administratorii trebuie să poată: adăuga filme, edita filme, șterge filme.
Sistemul trebuie să permită asocierea filmelor cu proiecții.
Sistemul trebuie să permită căutarea și sortarea filmelor.

# Management proiecții și săli
Sistemul trebuie să permită crearea de proiecții pentru filme.
Sistemul trebuie să permită asocierea unei proiecții cu o sală de cinema.
Sistemul trebuie să gestioneze locurile disponibile într-o sală.
Sistemul trebuie să permită vizualizarea locurilor disponibile pentru o proiecție.

# Management rezervă
Utilizatorii trebuie să poată: selecta o proiecție, selecta locuri disponibile, vizualiza rezervările proprii, anula rezervări.
Sistemul trebuie să permită crearea unei rezervări.
Sistemul trebuie să genereze bilete pentru fiecare loc rezervat.
Sistemul trebuie să prevină rezervarea dublă a aceluiași loc.
